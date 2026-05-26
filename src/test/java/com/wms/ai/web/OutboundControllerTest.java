package com.wms.ai.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.ai.coordinator.DispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc coverage for the outbound command endpoints: {@code POST
 * /api/workers/{id}/status} and {@code POST /api/tasks/{id}/status}. The error
 * contract (unknown id, illegal transition) is covered in Task 3.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:weboutboundtest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OutboundControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DispatchService dispatch;

    @Test
    void updateWorkerStatusParsesTheEnumAndReturnsTheTransitionedWorker() throws Exception {
        // WK-4 is seeded IDLE; IDLE -> OFFLINE is legal.
        mvc.perform(post("/api/workers/WK-4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("WK-4"))
                .andExpect(jsonPath("$.status").value("OFFLINE"));
    }

    @Test
    void updateTaskStatusParsesTheEnumAndReturnsTheTransitionedTask() throws Exception {
        // Mint a real task via the coordinator (SEED-ORD-1 -> WK-1), then advance it.
        var taskId = dispatch.assignOrderToWorker("SEED-ORD-1", "WK-1").task().id();

        mvc.perform(post("/api/tasks/" + taskId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.status").value("PICKING"));
    }
}
