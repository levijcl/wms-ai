package com.wms.ai.coordinator.internal;

import com.wms.ai.coordinator.DispatchResult;
import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.coordinator.WarehouseState;
import com.wms.ai.inventory.InventoryService;
import com.wms.ai.order.Order;
import com.wms.ai.order.OrderItem;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.PickingTask;
import com.wms.ai.outbound.WorkerStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the coordinator port. Owns no entity and no
 * repository — it is pure orchestration over the three business-module ports,
 * injected by constructor so the unidirectional "coordinator → ports" dependency is
 * explicit and the impl stays testable with the real beans.
 */
@Service
class DispatchServiceImpl implements DispatchService {

    private final InventoryService inventory;
    private final OrderService orders;
    private final OutboundService outbound;

    DispatchServiceImpl(InventoryService inventory, OrderService orders, OutboundService outbound) {
        this.inventory = inventory;
        this.orders = orders;
        this.outbound = outbound;
    }

    @Override
    public WarehouseState warehouseState() {
        return new WarehouseState(
                inventory.listAll(), orders.listAll(), outbound.listWorkers(), outbound.listTasks());
    }

    /**
     * The atomic composite (README §3.4, step order fixed): read the order, reserve
     * every line, advance the order to {@code ASSIGNED}, the worker to {@code BUSY},
     * then create the picking task. {@code @Transactional} makes all four steps one
     * unit — any guardrail failure rolls back the reserves already made and leaves the
     * order/worker untouched. The modules' exceptions surface unchanged; no check is
     * relaxed to force success (README §6).
     */
    @Override
    @Transactional
    public DispatchResult assignOrderToWorker(String orderId, String workerId) {
        Order order = orders.get(orderId)
                .orElseThrow(() -> new IllegalArgumentException("unknown order: " + orderId));

        for (OrderItem item : order.items()) {
            if (!inventory.reserve(item.sku(), item.quantity())) {
                throw new IllegalStateException("insufficient stock for " + item.sku());
            }
        }

        orders.updateStatus(orderId, OrderStatus.ASSIGNED);
        outbound.updateWorkerStatus(workerId, WorkerStatus.BUSY);
        PickingTask task = outbound.createTask(orderId, workerId);

        return new DispatchResult(task, orderId, workerId);
    }
}
