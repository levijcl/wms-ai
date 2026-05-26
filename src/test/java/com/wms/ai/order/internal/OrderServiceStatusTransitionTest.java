package com.wms.ai.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.ai.order.OrderStatus;
import com.wms.ai.order.Priority;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(OrderServiceImpl.class)
class OrderServiceStatusTransitionTest {

    @Autowired
    OrderRepository repository;

    @Autowired
    OrderServiceImpl service;

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
        "PENDING,ASSIGNED",
        "PENDING,CANCELLED",
        "ASSIGNED,PICKING",
        "ASSIGNED,CANCELLED",
        "PICKING,PICKED",
        "PICKING,CANCELLED",
        "PICKED,SHIPPED",
        "PICKED,CANCELLED",
    })
    void legalTransitionUpdatesStatus(OrderStatus from, OrderStatus to) {
        persist("ORD-1", from);

        var updated = service.updateStatus("ORD-1", to);

        assertThat(updated.status()).isEqualTo(to);
        assertThat(repository.findById("ORD-1").orElseThrow().getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
        // skipping forward / going backward
        "PENDING,PICKING",
        "PENDING,SHIPPED",
        "ASSIGNED,PENDING",
        "PICKING,ASSIGNED",
        "PICKED,PICKING",
        // no self-transitions
        "PENDING,PENDING",
        // terminal states cannot move
        "SHIPPED,PENDING",
        "SHIPPED,CANCELLED",
        "CANCELLED,PENDING",
        "CANCELLED,ASSIGNED",
    })
    void illegalTransitionThrowsAndLeavesStatusUnchanged(OrderStatus from, OrderStatus to) {
        persist("ORD-1", from);

        assertThatThrownBy(() -> service.updateStatus("ORD-1", to))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateStatusOfUnknownIdThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.updateStatus("NOPE", OrderStatus.ASSIGNED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void persist(String id, OrderStatus status) {
        repository.saveAndFlush(new OrderEntity(
                id,
                "ACME",
                List.of(new OrderItemEmbeddable("SKU-1", 1)),
                Priority.NORMAL,
                Instant.parse("2026-01-01T00:00:00Z"),
                status));
    }
}
