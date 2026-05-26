package com.wms.ai.outbound;

/**
 * Lifecycle state of a {@link PickingTask}. Forward path
 * {@code ASSIGNED → PICKING → DONE}; {@code CANCELLED} is reachable from any
 * non-terminal state. {@code DONE} and {@code CANCELLED} are terminal. The
 * transition rules are enforced in the service layer.
 */
public enum TaskStatus {
    ASSIGNED,
    PICKING,
    DONE,
    CANCELLED
}
