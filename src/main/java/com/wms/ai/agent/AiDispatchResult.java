package com.wms.ai.agent;

import java.util.List;

/**
 * The outcome of one AI dispatch cycle (README §3.7, §4.2). {@code outcomes} is the ground
 * truth of what the agent's write tool actually did this cycle (typically one assignment, or a
 * skip with its reason — single-best-per-cycle); {@code reasoning} is the model's own short
 * explanation of why. Both render into the console's event log next to the human's actions.
 *
 * @param outcomes  each assignment the agent attempted, with success/skip + detail
 * @param reasoning the model's plain-text rationale for this cycle's decision
 */
public record AiDispatchResult(List<AssignmentOutcome> outcomes, String reasoning) {}
