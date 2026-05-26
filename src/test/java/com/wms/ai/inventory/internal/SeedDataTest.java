package com.wms.ai.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.inventory.InventoryService;
import com.wms.ai.inventory.Stock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the dev-profile {@code CommandLineRunner} seeds sample stock on
 * startup. Runs in the {@code dev} profile with its own isolated in-memory DB so
 * it neither depends on nor pollutes the default-profile tests' shared database.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:seedtest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class SeedDataTest {

    @Autowired
    InventoryService service;

    @Autowired
    SeedData seedData;

    @Test
    void seedsSampleStockAcrossMultipleZonesOnStartup() {
        var all = service.listAll();

        assertThat(all)
                .extracting(Stock::sku)
                .contains("SKU-1001", "SKU-1002", "SKU-2001", "SKU-2002", "SKU-3001");
        assertThat(all)
                .extracting(Stock::location)
                .contains("ZONE-1", "ZONE-2", "ZONE-3");
    }

    @Test
    void reRunningTheSeederDoesNotDuplicate() {
        int afterStartup = service.listAll().size();

        seedData.run(); // simulate a restart

        assertThat(service.listAll()).hasSize(afterStartup);
    }
}
