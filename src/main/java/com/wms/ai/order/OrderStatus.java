package com.wms.ai.order;

/**
 * The order lifecycle states (README §3.2). Legal transitions between them are
 * enforced by the Order module's service layer, not by this enum — the guardrail
 * lives in the business layer per README §2.
 *
 * <p>Forward path: {@code PENDING → ASSIGNED → PICKING → PICKED → SHIPPED}. An
 * order may be {@code CANCELLED} from any non-terminal state. {@code SHIPPED} and
 * {@code CANCELLED} are terminal.
 */
public enum OrderStatus {
    PENDING,
    ASSIGNED,
    PICKING,
    PICKED,
    SHIPPED,
    CANCELLED
}
