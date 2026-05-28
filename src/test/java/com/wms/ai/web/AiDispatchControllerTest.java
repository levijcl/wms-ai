package com.wms.ai.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.ai.agent.AiDispatchResult;
import com.wms.ai.agent.AssignmentOutcome;
import com.wms.ai.agent.DispatchAgent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc coverage for {@code POST /api/dispatch/ai}. The {@link DispatchAgent} is mocked so the
 * test exercises only the controller's routing/serialization — no LLM call. A real round-trip is
 * the key-gated {@code DispatchAgentIntegrationTest}.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:webaitest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AiDispatchControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DispatchAgent agent;

    @Test
    void aiDispatchRunsOneCycleAndReturnsTheResultAsJson() throws Exception {
        when(agent.dispatchOnce()).thenReturn(new AiDispatchResult(
                List.of(new AssignmentOutcome("SEED-ORD-1", "WK-1", true, "created task T-1")),
                "Assigned the URGENT order SEED-ORD-1 to idle WK-1 in the same zone."));

        mvc.perform(post("/api/dispatch/ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasoning").value(
                        "Assigned the URGENT order SEED-ORD-1 to idle WK-1 in the same zone."))
                .andExpect(jsonPath("$.outcomes[0].orderId").value("SEED-ORD-1"))
                .andExpect(jsonPath("$.outcomes[0].workerId").value("WK-1"))
                .andExpect(jsonPath("$.outcomes[0].assigned").value(true))
                .andExpect(jsonPath("$.outcomes[0].detail").value("created task T-1"));
    }
}
