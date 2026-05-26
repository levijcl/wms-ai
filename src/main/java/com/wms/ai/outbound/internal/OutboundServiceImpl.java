package com.wms.ai.outbound.internal;

import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.PickingTask;
import com.wms.ai.outbound.TaskStatus;
import com.wms.ai.outbound.Worker;
import com.wms.ai.outbound.WorkerStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Package-private implementation of the Outbound port. Maps the package-private
 * {@link WorkerEntity} / {@link PickingTaskEntity} to the public {@code Worker} /
 * {@code PickingTask} records so JPA types never cross the module boundary.
 */
@Service
class OutboundServiceImpl implements OutboundService {

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
    public Worker updateWorkerStatus(String workerId, WorkerStatus newStatus) {
        throw new UnsupportedOperationException("not implemented yet"); // Task 3
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
