package com.wms.ai.outbound;

import java.time.Instant;

/**
 * Immutable public view of a picking task — part of the Outbound module's sealed
 * port. Callers see {@code PickingTask}, never the JPA {@code PickingTaskEntity}.
 *
 * @param id         service-generated identifier
 * @param orderId    the order this task fulfils — an opaque reference; Outbound has
 *                   no Order dependency and does not validate that the order exists
 * @param workerId   the worker the task is assigned to
 * @param assignedAt when the task was created/assigned
 * @param status     current lifecycle state
 */
public record PickingTask(
        String id, String orderId, String workerId, Instant assignedAt, TaskStatus status) {}
