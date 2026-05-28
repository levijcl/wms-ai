package com.wms.ai.agent;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * The Phase B dispatcher: an LLM agent that drives the same coordinator seam the human console
 * drives in Phase A (README §3.7). It runs one dispatch cycle by handing the model the §5 policy
 * (the system prompt) and the {@link DispatchTools}; the model reads state and calls the write
 * tool to assign the single best pending order. The agent only *coordinates* — it never touches
 * data directly, and every write goes through the guarded tool, so guardrails always run.
 *
 * <p>Spring AI {@code 2.0.0-M6} exposes no per-request max-iterations knob, so the loop is bounded
 * by the single-best prompt ("assign exactly one order, then stop") plus {@code temperature: 0};
 * the model converges to one assignment and a final message rather than recursing.
 */
@Component
public class DispatchAgent {

    private final ChatClient chatClient;
    private final DispatchTools tools;

    DispatchAgent(
            ChatClient.Builder chatClientBuilder,
            DispatchTools tools,
            @Value("classpath:/prompts/dispatcher-system.st") Resource systemPrompt) {
        this.tools = tools;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultTools(tools)
                .build();
    }

    /**
     * Run one dispatch cycle. Returns the ground-truth tool outcomes (drained from
     * {@link DispatchTools}) plus the model's reasoning text. At most one assignment is made.
     */
    public AiDispatchResult dispatchOnce() {
        tools.drainOutcomes(); // clear anything left from a prior cycle
        String reasoning = chatClient.prompt()
                .user("Run one dispatch cycle now: choose the single best pending order and "
                        + "assign it to the best available worker, or assign nothing if none is "
                        + "feasible. Then explain your decision briefly.")
                .call()
                .content();
        List<AssignmentOutcome> outcomes = tools.drainOutcomes();
        return new AiDispatchResult(outcomes, reasoning);
    }
}
