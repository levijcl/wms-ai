package com.wms.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * A real round-trip against Claude — gated on {@code ANTHROPIC_API_KEY}, so it runs locally with a
 * key and is skipped in CI (LLM calls are slow, cost money, and need a secret). Isolated DB; the
 * floor simulator stays off. Validates the §9 Phase B expectations: the agent picks the URGENT
 * in-stock order, makes exactly one assignment, never assigns the out-of-stock order, and explains
 * itself.
 *
 * <p>Seed reference (dev): SEED-ORD-1 = URGENT (SKU-1001 x2, in stock); SEED-ORD-4 = LOW
 * (SKU-2002 x1, out of stock); WK-1..4 IDLE.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agentlivetest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class DispatchAgentIntegrationTest {

    @Autowired
    DispatchAgent agent;

    @Autowired
    OrderService orders;

    @Test
    void dispatchesTheSingleBestOrderAndExplainsItself() {
        AiDispatchResult result = agent.dispatchOnce();

        assertThat(result.reasoning()).isNotBlank();

        // Exactly one successful assignment this cycle (single-best), and it is the URGENT order.
        assertThat(result.outcomes()).filteredOn(AssignmentOutcome::assigned).hasSize(1);
        AssignmentOutcome assigned = result.outcomes().stream()
                .filter(AssignmentOutcome::assigned).findFirst().orElseThrow();
        assertThat(assigned.orderId()).isEqualTo("SEED-ORD-1");

        // The out-of-stock order is never successfully assigned.
        assertThat(result.outcomes())
                .noneMatch(o -> o.orderId().equals("SEED-ORD-4") && o.assigned());

        // The assignment really took effect through the coordinator (guardrails ran).
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.ASSIGNED);
    }
}
