package com.wms.ai.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InventoryServiceImpl.class)
class InventoryServiceReserveTest {

    @Autowired
    StockRepository repository;

    @Autowired
    InventoryServiceImpl service;

    @Test
    void reserveDecrementsStockAndReturnsTrue() {
        repository.saveAndFlush(new StockEntity("SKU-1", 10, "ZONE-1"));

        assertThat(service.reserve("SKU-1", 4)).isTrue();
        assertThat(service.getStock("SKU-1").orElseThrow().quantity()).isEqualTo(6);
    }

    @Test
    void reserveAllowsReservingExactlyAllStock() {
        repository.saveAndFlush(new StockEntity("SKU-1", 10, "ZONE-1"));

        assertThat(service.reserve("SKU-1", 10)).isTrue();
        assertThat(service.getStock("SKU-1").orElseThrow().quantity()).isZero();
    }

    @Test
    void reserveReturnsFalseForInsufficientStock() {
        repository.saveAndFlush(new StockEntity("SKU-1", 3, "ZONE-1"));

        assertThat(service.reserve("SKU-1", 4)).isFalse();
        assertThat(service.getStock("SKU-1").orElseThrow().quantity())
                .as("stock must be untouched on a failed reserve")
                .isEqualTo(3);
    }

    @Test
    void reserveReturnsFalseForUnknownSku() {
        assertThat(service.reserve("NOPE", 1)).isFalse();
    }

    @Test
    void reserveRejectsZeroQuantity() {
        assertThatThrownBy(() -> service.reserve("SKU-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserveRejectsNegativeQuantity() {
        assertThatThrownBy(() -> service.reserve("SKU-1", -5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
