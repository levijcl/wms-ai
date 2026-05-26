package com.wms.ai.outbound.internal;

import com.wms.ai.outbound.WorkerStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA persistence model for a worker. Package-private so it never leaks past the
 * module boundary — the impl maps it to the public {@code Worker} record. The
 * status enum persists as a string so it stays stable across reordering and
 * readable in the H2 console.
 */
@Entity
@Table(name = "worker")
class WorkerEntity {

    @Id
    private String id;

    private String name;

    private String currentZone;

    @Enumerated(EnumType.STRING)
    private WorkerStatus status;

    /** Required by JPA. */
    protected WorkerEntity() {}

    WorkerEntity(String id, String name, String currentZone, WorkerStatus status) {
        this.id = id;
        this.name = name;
        this.currentZone = currentZone;
        this.status = status;
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getCurrentZone() {
        return currentZone;
    }

    WorkerStatus getStatus() {
        return status;
    }

    void setStatus(WorkerStatus status) {
        this.status = status;
    }
}
