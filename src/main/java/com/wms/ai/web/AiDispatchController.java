package com.wms.ai.web;

import com.wms.ai.agent.AiDispatchResult;
import com.wms.ai.agent.DispatchAgent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/dispatch/ai} — runs one Phase B AI dispatch cycle (README §3.7, §4.2): the
 * agent assigns the single best pending order through the coordinator and returns its decision
 * trace. The Phase B analog of the human clicking Assign; a thin controller that just delegates
 * to {@link DispatchAgent} and serializes the {@link AiDispatchResult}.
 */
@RestController
@RequestMapping("/api")
class AiDispatchController {

    private final DispatchAgent agent;

    AiDispatchController(DispatchAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/dispatch/ai")
    AiDispatchResult dispatch() {
        return agent.dispatchOnce();
    }
}
