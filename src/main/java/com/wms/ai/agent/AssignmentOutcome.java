package com.wms.ai.agent;

/**
 * The result of the AI's one write tool, {@code assignOrderToWorker}. A guardrail rejection
 * is reported here (with {@code assigned=false} and the module's verbatim message as
 * {@code detail}) rather than thrown, so the agent can <em>skip</em> the order and carry on
 * — the guardrail still ran and was never relaxed (README §6).
 *
 * @param orderId  the order the assignment was attempted for
 * @param workerId the worker it was attempted against
 * @param assigned whether the assignment succeeded
 * @param detail   on success, the created task id; on failure, the guardrail's message
 */
public record AssignmentOutcome(String orderId, String workerId, boolean assigned, String detail) {}
