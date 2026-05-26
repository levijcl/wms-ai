package com.wms.ai.inventory.internal;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds sample stock across zones so dispatch experiments (especially zone
 * affinity, and the "skip insufficient stock" case) have data to work with.
 *
 * <p>Scoped to the {@code dev} profile and idempotent — it never runs under the
 * default test profile and never duplicates rows on restart.
 */
@Component
@Profile("dev")
class SeedData implements CommandLineRunner {

    private final StockRepository repository;

    SeedData(StockRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return; // already seeded — keep restarts idempotent
        }
        repository.saveAll(List.of(
                new StockEntity("SKU-1001", 100, "ZONE-1"),
                new StockEntity("SKU-1002", 50, "ZONE-1"),
                new StockEntity("SKU-2001", 75, "ZONE-2"),
                new StockEntity("SKU-2002", 0, "ZONE-2"), // out of stock: exercises the skip-insufficient path
                new StockEntity("SKU-3001", 25, "ZONE-3")));
    }
}
