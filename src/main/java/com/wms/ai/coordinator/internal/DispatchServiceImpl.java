package com.wms.ai.coordinator.internal;

import com.wms.ai.coordinator.DispatchResult;
import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.coordinator.WarehouseState;
import com.wms.ai.inventory.InventoryService;
import com.wms.ai.order.OrderService;
import com.wms.ai.outbound.OutboundService;
import org.springframework.stereotype.Service;

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

    @Override
    public DispatchResult assignOrderToWorker(String orderId, String workerId) {
        throw new UnsupportedOperationException("Task 3");
    }
}
