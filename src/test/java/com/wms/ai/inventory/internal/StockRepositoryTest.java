package com.wms.ai.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class StockRepositoryTest {

    @Autowired
    StockRepository repository;

    @Test
    void savesAndFindsStockBySku() {
        repository.save(new StockEntity("SKU-1", 10, "ZONE-1"));

        var found = repository.findById("SKU-1");

        assertThat(found).isPresent();
        assertThat(found.get().getSku()).isEqualTo("SKU-1");
        assertThat(found.get().getQuantity()).isEqualTo(10);
        assertThat(found.get().getLocation()).isEqualTo("ZONE-1");
    }

    @Test
    void findByIdIsEmptyForUnknownSku() {
        assertThat(repository.findById("NOPE")).isEmpty();
    }
}
