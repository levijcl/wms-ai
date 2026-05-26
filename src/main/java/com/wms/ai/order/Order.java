package com.wms.ai.order;

import java.time.Instant;
import java.util.List;

/**
 * Immutable public view of an order — part of the Order module's sealed port.
 * Callers see {@code Order}, never the JPA {@code OrderEntity}.
 *
 * @param id       service-generated identifier
 * @param customer customer this order belongs to
 * @param items    lines to be picked
 * @param priority dispatch priority
 * @param dueAt    deadline by which the order should ship
 * @param status   current lifecycle state
 */
public record Order(
        String id,
        String customer,
        List<OrderItem> items,
        Priority priority,
        Instant dueAt,
        OrderStatus status) {}
