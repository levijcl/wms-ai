package com.wms.ai.outbound.internal;

import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.PickingTask;
import com.wms.ai.outbound.TaskStatus;
import com.wms.ai.outbound.Worker;
import com.wms.ai.outbound.WorkerStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the Outbound port. Maps the package-private
 * {@link WorkerEntity} / {@link PickingTaskEntity} to the public {@code Worker} /
 * {@code PickingTask} records so JPA types never cross the module boundary.
 */
@Service
class OutboundServiceImpl implements OutboundService {

    /**
     * Legal next states per current worker status. {@code IDLE} and {@code BUSY}
     * cycle freely and either may go {@code OFFLINE}; an {@code OFFLINE} worker
     * must return to {@code IDLE} before being assigned. No self-transitions — so
     * (re-)assigning a worker that is already {@code BUSY} is rejected (README §6).
     */
    private static final Map<WorkerStatus, Set<WorkerStatus>> ALLOWED_WORKER_TRANSITIONS = Map.of(
            WorkerStatus.IDLE, EnumSet.of(WorkerStatus.BUSY, WorkerStatus.OFFLINE),
            WorkerStatus.BUSY, EnumSet.of(WorkerStatus.IDLE, WorkerStatus.OFFLINE),
            WorkerStatus.OFFLINE, EnumSet.of(WorkerStatus.IDLE));

    private final WorkerRepository workers;
    private final PickingTaskRepository tasks;

    OutboundServiceImpl(WorkerRepository workers, PickingTaskRepository tasks) {
        this.workers = workers;
        this.tasks = tasks;
    }

    // --- Workers ---

    @Override
    public Optional<Worker> getWorker(String id) {
        return workers.findById(id).map(OutboundServiceImpl::toWorker);
    }

    @Override
    public List<Worker> listWorkersByStatus(WorkerStatus status) {
        return workers.findByStatus(status).stream().map(OutboundServiceImpl::toWorker).toList();
    }

    @Override
    @Transactional
    public Worker updateWorkerStatus(String workerId, WorkerStatus newStatus) {
        var entity = workers.findById(workerId)
                .orElseThrow(() -> new IllegalArgumentException("unknown worker: " + workerId));
        var current = entity.getStatus();
        if (!ALLOWED_WORKER_TRANSITIONS.get(current).contains(newStatus)) {
            throw new IllegalStateException(
                    "illegal worker transition: " + current + " -> " + newStatus);
        }
        entity.setStatus(newStatus);
        return toWorker(workers.save(entity));
    }

    // --- Picking tasks ---

    @Override
    public PickingTask createTask(String orderId, String workerId) {
        throw new UnsupportedOperationException("not implemented yet"); // Task 4
    }

    @Override
    public Optional<PickingTask> getTask(String id) {
        throw new UnsupportedOperationException("not implemented yet"); // Task 4
    }

    @Override
    public PickingTask updateTaskStatus(String taskId, TaskStatus newStatus) {
        throw new UnsupportedOperationException("not implemented yet"); // Task 5
    }

    private static Worker toWorker(WorkerEntity entity) {
        return new Worker(entity.getId(), entity.getName(), entity.getCurrentZone(), entity.getStatus());
    }
}
