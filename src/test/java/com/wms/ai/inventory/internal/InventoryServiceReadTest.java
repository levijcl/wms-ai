package com.wms.ai.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.inventory.Stock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InventoryServiceImpl.class)
class InventoryServiceReadTest {

    @Autowired
    StockRepository repository;

    @Autowired
    InventoryServiceImpl service;

    @Test
    void getStockReturnsValueForKnownSku() {
        repository.save(new StockEntity("SKU-1", 10, "ZONE-1"));

        assertThat(service.getStock("SKU-1"))
                .contains(new Stock("SKU-1", 10, "ZONE-1"));
    }

    @Test
    void getStockIsEmptyForUnknownSku() {
        assertThat(service.getStock("NOPE")).isEmpty();
    }

    @Test
    void listAllReturnsEveryRow() {
        repository.save(new StockEntity("SKU-1", 10, "ZONE-1"));
        repository.save(new StockEntity("SKU-2", 5, "ZONE-2"));

        assertThat(service.listAll())
                .containsExactlyInAnyOrder(
                        new Stock("SKU-1", 10, "ZONE-1"),
                        new Stock("SKU-2", 5, "ZONE-2"));
    }

    @Test
    void listAllIsEmptyWhenNoStock() {
        assertThat(service.listAll()).isEmpty();
    }
}
