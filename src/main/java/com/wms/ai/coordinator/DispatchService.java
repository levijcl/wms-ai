package com.wms.ai.coordinator;

/**
 * The coordinator module's single public entry point — its port and the
 * <strong>pluggable dispatch seam</strong> (README §3.4). It is the only thing that
 * wires the three business modules (Inventory / Order / Outbound) together; a human
 * console drives it in Phase A and the AI module drives it in Phase B, through this
 * same interface.
 *
 * <p>Unlike the business modules the coordinator owns <strong>no entity</strong>: it
 * is pure orchestration over the three public ports. Callers depend only on this
 * interface and the {@link WarehouseState}/{@link DispatchResult} view records; the
 * {@code @Service} implementation is package-private under {@code internal}.
 *
 * <p>The coordinator decides <em>nothing</em> about which assignment to make — that
 * judgement is the dispatcher's. It only <em>executes</em> an assignment atomically
 * and <em>reads</em> aggregated state, surfacing the modules' guardrail exceptions
 * unchanged (it never relaxes a check to force success — README §6).
 */
public interface DispatchService {

    /**
     * Aggregate the three modules into one read-only snapshot — the single payload
     * the visualization polls (README §3.6, §7).
     */
    WarehouseState warehouseState();

    /**
     * Execute the atomic composite "reserve stock + Order → ASSIGNED + Worker → BUSY
     * + create task" for {@code orderId}/{@code workerId} as one transaction, so a
     * guardrail failure at any step rolls back everything already applied.
     *
     * @return the outcome carrying the created {@code PickingTask}
     * @throws IllegalArgumentException if {@code orderId} is unknown
     * @throws IllegalStateException    if any guardrail rejects — insufficient stock,
     *                                  a non-PENDING order, or a non-IDLE/raced worker
     */
    DispatchResult assignOrderToWorker(String orderId, String workerId);

    /**
     * Advance an in-flight pick one operator step, as one transaction. This is the floor's
     * twin to {@link #assignOrderToWorker}: the planner decides the assignment; the operator
     * (here, the floor simulator) executes the pick. Keyed on the order's current status, it
     * couples the order, task and worker forward to the next milestone:
     *
     * <ul>
     *   <li>{@code ASSIGNED} → task {@code PICKING} + order {@code PICKING}
     *   <li>{@code PICKING}  → order {@code PICKED}
     *   <li>{@code PICKED}   → order {@code SHIPPED} + task {@code DONE} + worker {@code IDLE}
     * </ul>
     *
     * <p>Each sub-transition is guarded by the sub-entity's current status, so a partial or
     * raced combo never issues an illegal move. A terminal or not-yet-assigned order is a
     * no-op. Like the assign composite it only <em>executes</em> — it never relaxes a check.
     *
     * @throws IllegalArgumentException if {@code taskId} is unknown
     */
    void advancePick(String taskId);
}
