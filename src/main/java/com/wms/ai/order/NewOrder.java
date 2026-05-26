package com.wms.ai.order;

import java.time.Instant;
import java.util.List;

/**
 * Input to {@link OrderService#submit(NewOrder)} — an incoming order before it
 * enters the system. Carries no {@code id} or {@code status}: the service assigns
 * a generated id and the initial {@code PENDING} status.
 *
 * @param customer customer placing the order (required, non-blank)
 * @param items    lines to be picked (required, non-empty; each with a non-blank
 *                 SKU and positive quantity)
 * @param priority dispatch priority (required)
 * @param dueAt    deadline by which the order should ship (required)
 */
public record NewOrder(
        String customer,
        List<OrderItem> items,
        Priority priority,
        Instant dueAt) {}
