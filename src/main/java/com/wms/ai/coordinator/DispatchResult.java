package com.wms.ai.coordinator;

import com.wms.ai.outbound.PickingTask;

/**
 * The outcome of a successful {@link DispatchService#assignOrderToWorker} — the
 * picking task that was created plus the order and worker it links. A guardrail
 * failure produces an exception, not a {@code DispatchResult}, so this record always
 * represents a completed assignment.
 *
 * @param task     the newly created picking task (status {@code ASSIGNED})
 * @param orderId  the order that was assigned (now {@code ASSIGNED})
 * @param workerId the worker it was assigned to (now {@code BUSY})
 */
public record DispatchResult(PickingTask task, String orderId, String workerId) {}
