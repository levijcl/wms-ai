package com.wms.ai.order;

import java.util.List;
import java.util.Optional;

/**
 * The Order module's single public entry point — its port.
 *
 * <p>Manages the order lifecycle: accepts incoming orders, exposes queries, and
 * enforces legal state transitions. The transition guardrail lives here in the
 * service layer, never in a caller or a prompt. This module does not mutate stock
 * or assign workers — the AI coordinator wires those together.
 *
 * <p>Callers depend only on this interface and the view/input records
 * ({@link Order}, {@link OrderItem}, {@link NewOrder}) plus the {@link Priority}
 * and {@link OrderStatus} enums; the JPA entity, repository, and implementation
 * are package-private under {@code internal}.
 */
public interface OrderService {

    /**
     * Accept and persist an incoming order. Assigns a generated id and an initial
     * status of {@link OrderStatus#PENDING}.
     *
     * @return the created order, including its generated id
     * @throws IllegalArgumentException if the draft is invalid (blank customer,
     *         empty items, non-positive quantity, blank SKU, or null priority/dueAt)
     */
    Order submit(NewOrder draft);

    /** The order with this id, or empty if unknown. */
    Optional<Order> get(String id);

    /** All orders currently in the given status. */
    List<Order> listByStatus(OrderStatus status);

    /** All orders. */
    List<Order> listAll();

    /**
     * Transition an order to {@code newStatus}.
     *
     * @return the updated order
     * @throws IllegalArgumentException if no order has this id
     * @throws IllegalStateException    if the transition is not legal from the
     *         order's current status (including any move out of a terminal state)
     */
    Order updateStatus(String id, OrderStatus newStatus);
}
