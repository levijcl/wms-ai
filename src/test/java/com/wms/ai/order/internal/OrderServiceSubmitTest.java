package com.wms.ai.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.ai.order.NewOrder;
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
class OrderServiceSubmitTest {

    private static final Instant DUE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    OrderRepository repository;

    @Autowired
    OrderServiceImpl service;

    private static NewOrder validDraft() {
        return new NewOrder(
                "ACME",
                List.of(new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 1)),
                Priority.HIGH,
                DUE);
    }

    @Test
    void submitReturnsOrderWithGeneratedIdAndPendingStatus() {
        var order = service.submit(validDraft());

        assertThat(order.id()).isNotBlank();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.customer()).isEqualTo("ACME");
        assertThat(order.priority()).isEqualTo(Priority.HIGH);
        assertThat(order.dueAt()).isEqualTo(DUE);
        assertThat(order.items())
                .containsExactlyInAnyOrder(new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 1));
    }

    @Test
    void submitPersistsTheOrderAsPending() {
        var order = service.submit(validDraft());

        var found = repository.findById(order.id());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.get().getItems()).hasSize(2);
    }

    @Test
    void submitAssignsAUniqueIdPerOrder() {
        var first = service.submit(validDraft());
        var second = service.submit(validDraft());

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void submitRejectsNullDraft() {
        assertThatThrownBy(() -> service.submit(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitRejectsBlankCustomer() {
        var draft = new NewOrder("  ", List.of(new OrderItem("SKU-1", 1)), Priority.LOW, DUE);
        assertThatThrownBy(() -> service.submit(draft)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitRejectsEmptyItems() {
        var draft = new NewOrder("ACME", List.of(), Priority.LOW, DUE);
        assertThatThrownBy(() -> service.submit(draft)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitRejectsNonPositiveItemQuantity() {
        var draft = new NewOrder("ACME", List.of(new OrderItem("SKU-1", 0)), Priority.LOW, DUE);
        assertThatThrownBy(() -> service.submit(draft)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitRejectsBlankItemSku() {
        var draft = new NewOrder("ACME", List.of(new OrderItem(" ", 1)), Priority.LOW, DUE);
        assertThatThrownBy(() -> service.submit(draft)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitRejectsNullPriority() {
        var draft = new NewOrder("ACME", List.of(new OrderItem("SKU-1", 1)), null, DUE);
        assertThatThrownBy(() -> service.submit(draft)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitRejectsNullDueAt() {
        var draft = new NewOrder("ACME", List.of(new OrderItem("SKU-1", 1)), Priority.LOW, null);
        assertThatThrownBy(() -> service.submit(draft)).isInstanceOf(IllegalArgumentException.class);
    }
}
