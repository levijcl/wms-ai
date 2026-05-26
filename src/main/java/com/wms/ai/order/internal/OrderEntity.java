package com.wms.ai.order.internal;

import com.wms.ai.order.OrderStatus;
import com.wms.ai.order.Priority;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA persistence model for an order. Package-private so it never leaks past the
 * module boundary — the impl maps it to the public {@code Order} record.
 *
 * <p>Items are an {@code @ElementCollection} of value-typed {@link OrderItemEmbeddable}s
 * owned by the order. The collection is LAZY (the default), so read methods on the
 * service must map it inside a transaction. Enums persist as strings so they stay
 * stable across reordering and readable in the H2 console.
 */
@Entity
@Table(name = "orders") // "order" is a reserved SQL word — name the table explicitly
class OrderEntity {

    @Id
    private String id;

    private String customer;

    @ElementCollection
    @CollectionTable(name = "order_item", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItemEmbeddable> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private Priority priority;

    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    /** Required by JPA. */
    protected OrderEntity() {}

    OrderEntity(
            String id,
            String customer,
            List<OrderItemEmbeddable> items,
            Priority priority,
            Instant dueAt,
            OrderStatus status) {
        this.id = id;
        this.customer = customer;
        this.items = new ArrayList<>(items);
        this.priority = priority;
        this.dueAt = dueAt;
        this.status = status;
    }

    String getId() {
        return id;
    }

    String getCustomer() {
        return customer;
    }

    List<OrderItemEmbeddable> getItems() {
        return items;
    }

    Priority getPriority() {
        return priority;
    }

    Instant getDueAt() {
        return dueAt;
    }

    OrderStatus getStatus() {
        return status;
    }

    void setStatus(OrderStatus status) {
        this.status = status;
    }
}
