package com.wms.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The write tool records the ground truth of each assignment it attempts, so the agent can build
 * an accurate trace from what actually happened (not the model's self-report). Isolated DB so the
 * mutations here don't perturb the read-tool assertions in {@link DispatchToolsTest}.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agentrecordingtest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class DispatchToolsRecordingTest {

    @Autowired
    DispatchTools tools;

    @Test
    void recordsEachAssignmentOutcomeAndDrainReturnsThenClears() {
        tools.assignOrderToWorker("SEED-ORD-1", "WK-1"); // succeeds
        tools.assignOrderToWorker("SEED-ORD-4", "WK-2"); // out-of-stock → skip

        List<AssignmentOutcome> drained = tools.drainOutcomes();

        assertThat(drained).hasSize(2);
        assertThat(drained).anyMatch(o -> o.orderId().equals("SEED-ORD-1") && o.assigned());
        assertThat(drained).anyMatch(o -> o.orderId().equals("SEED-ORD-4") && !o.assigned());
        assertThat(tools.drainOutcomes()).isEmpty(); // drained list was cleared
    }
}
