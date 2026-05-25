package com.wms.ai.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InventoryServiceImpl.class)
class InventoryServiceReleaseTest {

    @Autowired
    StockRepository repository;

    @Autowired
    InventoryServiceImpl service;

    @Test
    void releaseIncrementsStock() {
        repository.saveAndFlush(new StockEntity("SKU-1", 5, "ZONE-1"));

        service.release("SKU-1", 3);

        assertThat(service.getStock("SKU-1").orElseThrow().quantity()).isEqualTo(8);
    }

    @Test
    void reserveThenReleaseRestoresOriginalLevel() {
        repository.saveAndFlush(new StockEntity("SKU-1", 10, "ZONE-1"));

        assertThat(service.reserve("SKU-1", 4)).isTrue();
        service.release("SKU-1", 4);

        assertThat(service.getStock("SKU-1").orElseThrow().quantity()).isEqualTo(10);
    }

    @Test
    void releaseOfUnknownSkuIsANoOp() {
        service.release("NOPE", 3);

        assertThat(service.getStock("NOPE")).isEmpty();
    }

    @Test
    void releaseRejectsZeroQuantity() {
        assertThatThrownBy(() -> service.release("SKU-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseRejectsNegativeQuantity() {
        assertThatThrownBy(() -> service.release("SKU-1", -2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
