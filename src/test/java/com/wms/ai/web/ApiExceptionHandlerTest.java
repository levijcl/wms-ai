package com.wms.ai.web;

import static org.hamcrest.Matchers.containsString;
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
 * Verifies the {@code @RestControllerAdvice} maps the module error contract to HTTP so
 * the console event log can show <em>why</em> a dispatch was rejected (README §6, §7):
 * {@code IllegalArgumentException} → 400, {@code IllegalStateException} → 409, each with
 * a JSON {@code {error, message}} body carrying the exception's own message verbatim.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:weberrortest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void insufficientStockAssignReturns409WithReadableMessage() throws Exception {
        // SEED-ORD-4 needs SKU-2002, seeded at 0 → IllegalStateException.
        mvc.perform(post("/api/dispatch/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"SEED-ORD-4\",\"workerId\":\"WK-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IllegalStateException"))
                .andExpect(jsonPath("$.message", containsString("SKU-2002")));
    }

    @Test
    void unknownOrderAssignReturns400() throws Exception {
        mvc.perform(post("/api/dispatch/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"NO-SUCH-ORDER\",\"workerId\":\"WK-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IllegalArgumentException"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void illegalOrderStatusTransitionReturns409() throws Exception {
        // SEED-ORD-3 is PENDING; PENDING -> SHIPPED is not a legal transition.
        mvc.perform(post("/api/orders/SEED-ORD-3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IllegalStateException"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unknownEnumStringReturns400() throws Exception {
        // The controller's valueOf conversion throws IllegalArgumentException → clean 400.
        mvc.perform(post("/api/orders/SEED-ORD-2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NONSENSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IllegalArgumentException"));
    }

    @Test
    void blankFieldOnOrderSubmitReturns400() throws Exception {
        // Blank customer fails OrderService validation → IllegalArgumentException → 400.
        String body = """
                {
                  "customer": "",
                  "items": [ { "sku": "SKU-1001", "quantity": 1 } ],
                  "priority": "LOW",
                  "dueAt": "2026-12-31T00:00:00Z"
                }
                """;

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IllegalArgumentException"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
