package com.wms.ai.agent;

import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.inventory.InventoryService;
import com.wms.ai.inventory.Stock;
import com.wms.ai.order.Order;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.Worker;
import com.wms.ai.outbound.WorkerStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The tools exposed to the LLM dispatcher (README §3.7). Thin wrappers over the public
 * ports for reads and over the coordinator for the single write, so the AI can only
 * <em>read state</em> and <em>request an assignment</em> — never touch data directly and
 * never bypass a guardrail. The write tool reports a guardrail rejection as an
 * {@link AssignmentOutcome} (it never throws), so a wrong instruction makes the model skip
 * the order rather than aborting the run, and the check is never relaxed (README §6).
 */
@Component
class DispatchTools {

    private final OrderService orders;
    private final InventoryService inventory;
    private final OutboundService outbound;
    private final DispatchService dispatch;

    // Ground-truth record of what the write tool actually did this cycle. The agent drains it
    // after a run to build the trace, rather than trusting the model's self-report. (Single-user
    // dev experiment; synchronized for safety but not designed for concurrent cycles.)
    private final List<AssignmentOutcome> recorded = Collections.synchronizedList(new ArrayList<>());

    DispatchTools(
            OrderService orders,
            InventoryService inventory,
            OutboundService outbound,
            DispatchService dispatch) {
        this.orders = orders;
        this.inventory = inventory;
        this.outbound = outbound;
        this.dispatch = dispatch;
    }

    @Tool(description = "List all orders awaiting dispatch (status PENDING), with customer, "
            + "priority, dueAt and item lines.")
    public List<Order> listPendingOrders() {
        return orders.listByStatus(OrderStatus.PENDING);
    }

    @Tool(description = "Get the current stock (quantity and location) for a SKU, or null if the "
            + "SKU is unknown.")
    public Stock getStock(@ToolParam(description = "the stock-keeping unit, e.g. SKU-1001") String sku) {
        return inventory.getStock(sku).orElse(null);
    }

    @Tool(description = "List workers available to be assigned work (status IDLE), with their "
            + "current zone.")
    public List<Worker> listAvailableWorkers() {
        return outbound.listWorkersByStatus(WorkerStatus.IDLE);
    }

    @Tool(description = "Assign a PENDING order to an IDLE worker. Reserves stock, advances the "
            + "order and worker, and creates a picking task — atomically. If a guardrail rejects "
            + "(insufficient stock, order not PENDING, worker not IDLE), the outcome has "
            + "assigned=false and the reason in detail; skip that order and try another.")
    public AssignmentOutcome assignOrderToWorker(
            @ToolParam(description = "the PENDING order's id") String orderId,
            @ToolParam(description = "the IDLE worker's id") String workerId) {
        AssignmentOutcome outcome;
        try {
            var result = dispatch.assignOrderToWorker(orderId, workerId);
            outcome = new AssignmentOutcome(orderId, workerId, true, "created task " + result.task().id());
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            // Guardrail rejection — report it so the model skips this order; never relax it (§6).
            outcome = new AssignmentOutcome(orderId, workerId, false, rejected.getMessage());
        }
        recorded.add(outcome);
        return outcome;
    }

    /** Return the outcomes recorded since the last drain and clear them (used by the agent per cycle). */
    List<AssignmentOutcome> drainOutcomes() {
        synchronized (recorded) {
            List<AssignmentOutcome> snapshot = List.copyOf(recorded);
            recorded.clear();
            return snapshot;
        }
    }
}
