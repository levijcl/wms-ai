package com.wms.ai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.ai.inventory.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc coverage for the optional {@code POST /api/inventory/release} convenience
 * endpoint — restock used on cancellation/failure.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:webinvtest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InventoryControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InventoryService inventory;

    @Test
    void releaseRestocksTheSku() throws Exception {
        int before = inventory.getStock("SKU-3001").orElseThrow().quantity();

        mvc.perform(post("/api/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-3001\",\"quantity\":5}"))
                .andExpect(status().isOk());

        assertThat(inventory.getStock("SKU-3001").orElseThrow().quantity()).isEqualTo(before + 5);
    }
}
