package com.wms.ai.outbound;

import java.util.List;
import java.util.Optional;

/**
 * The Outbound module's single public entry point — its port.
 *
 * <p>Manages the executional side of outbound: the {@link Worker} labour pool and
 * the {@link PickingTask}s assigned to them. Guardrails (legal worker/task status
 * transitions, valid task creation) live here in the service layer, never in a
 * caller or a prompt. Callers depend only on this interface and the
 * {@code Worker}/{@code PickingTask} views; the JPA entities, repositories, and
 * implementation are package-private under {@code internal}.
 *
 * <p>This module does <strong>not</strong> decide which order goes to which worker,
 * reserve stock, or change order state — those belong to the AI coordinator and the
 * Inventory/Order modules. In particular {@code orderId} is treated as opaque.
 */
public interface OutboundService {

    /** Current worker for an id, or empty if the id is unknown. */
    Optional<Worker> getWorker(String id);

    /** All workers currently in the given status (e.g. {@code IDLE} for dispatch). */
    List<Worker> listWorkersByStatus(WorkerStatus status);

    /**
     * Every worker, in any status — the whole labour pool. Backs the coordinator's
     * {@code warehouseState()} snapshot, which needs all workers rather than a single
     * status slice.
     */
    List<Worker> listWorkers();

    /**
     * Transition a worker to {@code newStatus}.
     *
     * @return the updated worker
     * @throws IllegalArgumentException if the worker id is unknown
     * @throws IllegalStateException    if the transition is not legal (e.g. assigning
     *                                  a worker that is no longer {@code IDLE})
     */
    Worker updateWorkerStatus(String workerId, WorkerStatus newStatus);

    /**
     * Create a picking task for {@code orderId} assigned to {@code workerId}, in
     * status {@code ASSIGNED} stamped with the current time.
     *
     * @return the created task
     * @throws IllegalArgumentException if {@code orderId} or {@code workerId} is
     *                                  blank, or {@code workerId} is not a known worker
     */
    PickingTask createTask(String orderId, String workerId);

    /** Current task for an id, or empty if the id is unknown. */
    Optional<PickingTask> getTask(String id);

    /**
     * Every picking task created so far, in any status. Backs the coordinator's
     * {@code warehouseState()} snapshot and the console's picking-task list.
     */
    List<PickingTask> listTasks();

    /**
     * Transition a picking task to {@code newStatus}.
     *
     * @return the updated task
     * @throws IllegalArgumentException if the task id is unknown
     * @throws IllegalStateException    if the transition is not legal
     */
    PickingTask updateTaskStatus(String taskId, TaskStatus newStatus);
}
