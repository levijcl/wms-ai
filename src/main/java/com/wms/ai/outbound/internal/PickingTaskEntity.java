package com.wms.ai.outbound.internal;

import com.wms.ai.outbound.TaskStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA persistence model for a picking task. Package-private so it never leaks past
 * the module boundary — the impl maps it to the public {@code PickingTask} record.
 * {@code orderId} is a plain string reference; Outbound has no Order dependency.
 */
@Entity
@Table(name = "picking_task")
class PickingTaskEntity {

    @Id
    private String id;

    private String orderId;

    private String workerId;

    private Instant assignedAt;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    /** Required by JPA. */
    protected PickingTaskEntity() {}

    PickingTaskEntity(String id, String orderId, String workerId, Instant assignedAt, TaskStatus status) {
        this.id = id;
        this.orderId = orderId;
        this.workerId = workerId;
        this.assignedAt = assignedAt;
        this.status = status;
    }

    String getId() {
        return id;
    }

    String getOrderId() {
        return orderId;
    }

    String getWorkerId() {
        return workerId;
    }

    Instant getAssignedAt() {
        return assignedAt;
    }

    TaskStatus getStatus() {
        return status;
    }

    void setStatus(TaskStatus status) {
        this.status = status;
    }
}
