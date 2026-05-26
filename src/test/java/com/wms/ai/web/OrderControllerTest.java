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
 * MockMvc coverage for the order command endpoints: {@code POST /api/orders}
 * (submission) and {@code POST /api/orders/{id}/status} (lifecycle transition). The
 * error contract (bad input, illegal transition) is covered in Task 3.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:webordertest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OrderControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void submitBindsNewOrderAndReturnsCreatedOrderWithIdAndPendingStatus() throws Exception {
        String body = """
                {
                  "customer": "Wayne Enterprises",
                  "items": [ { "sku": "SKU-1001", "quantity": 3 } ],
                  "priority": "HIGH",
                  "dueAt": "2026-12-31T00:00:00Z"
                }
                """;

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.customer").value("Wayne Enterprises"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-1001"))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void updateStatusParsesTheEnumAndReturnsTheTransitionedOrder() throws Exception {
        // SEED-ORD-2 starts PENDING; PENDING -> ASSIGNED is legal.
        mvc.perform(post("/api/orders/SEED-ORD-2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("SEED-ORD-2"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }
}
