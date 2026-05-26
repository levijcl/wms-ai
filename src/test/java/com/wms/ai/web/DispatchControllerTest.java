package com.wms.ai.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc coverage for {@code POST /api/dispatch/assign} — the happy path that runs
 * the coordinator's atomic composite. Guardrail-failure mapping (409/400) is covered
 * by {@code ApiExceptionHandlerTest} (Task 3).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:webdispatchtest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DispatchControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void assignReturnsDispatchResultForSeededPendingOrderAndIdleWorker() throws Exception {
        // SEED-ORD-1 is PENDING (SKU-1001 x2); WK-1 is IDLE.
        mvc.perform(post("/api/dispatch/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"SEED-ORD-1\",\"workerId\":\"WK-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("SEED-ORD-1"))
                .andExpect(jsonPath("$.workerId").value("WK-1"))
                .andExpect(jsonPath("$.task.orderId").value("SEED-ORD-1"))
                .andExpect(jsonPath("$.task.workerId").value("WK-1"))
                .andExpect(jsonPath("$.task.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.task.id").isNotEmpty());
    }
}
