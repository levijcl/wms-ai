package com.wms.ai.web;

import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.coordinator.WarehouseState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single read the SPA polls: {@code GET /api/state} returns the coordinator's
 * aggregated {@link WarehouseState} (stocks, orders, workers, tasks) as JSON. A thin
 * controller with no logic of its own — it just delegates to the public port.
 */
@RestController
@RequestMapping("/api")
class StateController {

    private final DispatchService dispatch;

    StateController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/state")
    WarehouseState state() {
        return dispatch.warehouseState();
    }
}
