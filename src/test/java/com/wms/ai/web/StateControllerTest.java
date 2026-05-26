package com.wms.ai.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * MockMvc coverage for {@code GET /api/state} — the single read the SPA polls. Runs
 * on the {@code dev} profile with an isolated in-memory DB so the seeded warehouse is
 * present, mirroring the module seed-data and coordinator tests.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:webstatetest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StateControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getStateReturnsTheFourAggregatedListsWithSeededData() throws Exception {
        mvc.perform(get("/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks").isArray())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.workers").isArray())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.stocks[*].sku", hasItem("SKU-1001")))
                .andExpect(jsonPath("$.orders[*].id", hasItem("SEED-ORD-1")))
                .andExpect(jsonPath("$.workers[*].id", hasItem("WK-1")));
    }

    @Test
    void getStateSerializesInstantFieldsAsIso8601Strings() throws Exception {
        // dueAt must be an ISO-8601 string, not an epoch number, so the SPA can parse it.
        mvc.perform(get("/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].dueAt").isString());
    }
}
