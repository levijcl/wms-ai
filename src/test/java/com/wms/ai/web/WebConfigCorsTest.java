package com.wms.ai.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the dev-profile CORS fallback: when the SPA is run cross-origin on the Vite
 * dev server ({@code http://localhost:5173}) instead of via the proxy, {@code /api/**}
 * responses carry the {@code Access-Control-Allow-Origin} header so the browser permits
 * them.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:webcorstest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class WebConfigCorsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiRoutesAllowTheViteDevOrigin() throws Exception {
        mvc.perform(get("/api/state").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
