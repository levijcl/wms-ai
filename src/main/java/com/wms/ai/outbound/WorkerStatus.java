package com.wms.ai.outbound;

/**
 * Lifecycle state of a {@link Worker}. Only an {@code IDLE} worker can be assigned
 * new work; the transition rules are enforced in the service layer.
 */
public enum WorkerStatus {
    IDLE,
    BUSY,
    OFFLINE
}
