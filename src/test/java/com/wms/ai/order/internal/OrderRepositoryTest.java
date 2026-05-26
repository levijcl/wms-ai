package com.wms.ai.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.wms.ai.order.OrderStatus;
import com.wms.ai.order.Priority;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    OrderRepository repository;

    @Test
    void savesAndFindsOrderWithItsItems() {
        repository.saveAndFlush(new OrderEntity(
                "ORD-1",
                "ACME",
                List.of(new OrderItemEmbeddable("SKU-1", 2), new OrderItemEmbeddable("SKU-2", 5)),
                Priority.HIGH,
                Instant.parse("2026-01-01T00:00:00Z"),
                OrderStatus.PENDING));

        var found = repository.findById("ORD-1");

        assertThat(found).isPresent();
        assertThat(found.get().getCustomer()).isEqualTo("ACME");
        assertThat(found.get().getPriority()).isEqualTo(Priority.HIGH);
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.get().getDueAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(found.get().getItems())
                .extracting(OrderItemEmbeddable::getSku, OrderItemEmbeddable::getQuantity)
                .containsExactlyInAnyOrder(tuple("SKU-1", 2), tuple("SKU-2", 5));
    }

    @Test
    void findByStatusReturnsOnlyMatchingOrders() {
        repository.saveAndFlush(order("ORD-1", OrderStatus.PENDING));
        repository.saveAndFlush(order("ORD-2", OrderStatus.SHIPPED));
        repository.saveAndFlush(order("ORD-3", OrderStatus.PENDING));

        assertThat(repository.findByStatus(OrderStatus.PENDING))
                .extracting(OrderEntity::getId)
                .containsExactlyInAnyOrder("ORD-1", "ORD-3");
    }

    @Test
    void findByIdIsEmptyForUnknownId() {
        assertThat(repository.findById("NOPE")).isEmpty();
    }

    private static OrderEntity order(String id, OrderStatus status) {
        return new OrderEntity(
                id,
                "ACME",
                List.of(new OrderItemEmbeddable("SKU-1", 1)),
                Priority.NORMAL,
                Instant.parse("2026-01-01T00:00:00Z"),
                status);
    }
}
