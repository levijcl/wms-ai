package com.wms.ai.order.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.order.NewOrder;
import com.wms.ai.order.Order;
import com.wms.ai.order.OrderItem;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.order.Priority;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(OrderServiceImpl.class)
class OrderServiceReadTest {

    private static final Instant DUE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    OrderRepository repository;

    @Autowired
    OrderServiceImpl service;

    @Test
    void getReturnsOrderWithItemsForKnownId() {
        var submitted = service.submit(new NewOrder(
                "ACME",
                List.of(new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 5)),
                Priority.HIGH,
                DUE));

        var found = service.get(submitted.id());

        assertThat(found).isPresent();
        Order order = found.get();
        assertThat(order.id()).isEqualTo(submitted.id());
        assertThat(order.customer()).isEqualTo("ACME");
        assertThat(order.priority()).isEqualTo(Priority.HIGH);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.dueAt()).isEqualTo(DUE);
        assertThat(order.items())
                .containsExactlyInAnyOrder(new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 5));
    }

    @Test
    void getIsEmptyForUnknownId() {
        assertThat(service.get("NOPE")).isEmpty();
    }

    @Test
    void listByStatusReturnsOnlyMatchingOrders() {
        persist("ORD-P1", OrderStatus.PENDING);
        persist("ORD-P2", OrderStatus.PENDING);
        persist("ORD-S1", OrderStatus.SHIPPED);

        assertThat(service.listByStatus(OrderStatus.PENDING))
                .extracting(Order::id)
                .containsExactlyInAnyOrder("ORD-P1", "ORD-P2");
        assertThat(service.listByStatus(OrderStatus.SHIPPED))
                .extracting(Order::id)
                .containsExactly("ORD-S1");
        assertThat(service.listByStatus(OrderStatus.CANCELLED)).isEmpty();
    }

    @Test
    void listAllReturnsEveryOrder() {
        persist("ORD-1", OrderStatus.PENDING);
        persist("ORD-2", OrderStatus.ASSIGNED);

        assertThat(service.listAll())
                .extracting(Order::id)
                .containsExactlyInAnyOrder("ORD-1", "ORD-2");
    }

    @Test
    void listAllIsEmptyWhenNoOrders() {
        assertThat(service.listAll()).isEmpty();
    }

    private void persist(String id, OrderStatus status) {
        repository.saveAndFlush(new OrderEntity(
                id,
                "ACME",
                List.of(new OrderItemEmbeddable("SKU-1", 1)),
                Priority.NORMAL,
                DUE,
                status));
    }
}
