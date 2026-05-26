package com.wms.ai.web;

import com.wms.ai.coordinator.DispatchResult;
import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.web.dto.AssignRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/dispatch/assign} — runs the coordinator's atomic composite. A thin
 * controller: it forwards the chosen order/worker to {@link DispatchService} and
 * returns the {@link DispatchResult}; any guardrail rejection surfaces as the module's
 * exception, mapped to 400/409 by {@code ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api")
class DispatchController {

    private final DispatchService dispatch;

    DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @PostMapping("/dispatch/assign")
    DispatchResult assign(@RequestBody AssignRequest request) {
        return dispatch.assignOrderToWorker(request.orderId(), request.workerId());
    }
}
