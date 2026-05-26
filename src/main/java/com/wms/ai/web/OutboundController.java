package com.wms.ai.web;

import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.PickingTask;
import com.wms.ai.outbound.TaskStatus;
import com.wms.ai.outbound.Worker;
import com.wms.ai.outbound.WorkerStatus;
import com.wms.ai.web.dto.StatusRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbound command endpoints: advance a worker's or a task's status. The status
 * string is converted to the target enum here (unknown value → clean 400); the
 * transition guardrails live in {@link OutboundService}.
 */
@RestController
@RequestMapping("/api")
class OutboundController {

    private final OutboundService outbound;

    OutboundController(OutboundService outbound) {
        this.outbound = outbound;
    }

    @PostMapping("/workers/{id}/status")
    Worker updateWorkerStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return outbound.updateWorkerStatus(id, WorkerStatus.valueOf(request.status()));
    }

    @PostMapping("/tasks/{id}/status")
    PickingTask updateTaskStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return outbound.updateTaskStatus(id, TaskStatus.valueOf(request.status()));
    }
}
