package com.wms.ai.inventory.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * JPA persistence model for stock. Package-private so it never leaks past the
 * module boundary — the impl maps it to the public {@code Stock} record.
 */
@Entity
class StockEntity {

    @Id
    private String sku;
    private int quantity;
    private String location;

    /** Required by JPA. */
    protected StockEntity() {}

    StockEntity(String sku, int quantity, String location) {
        this.sku = sku;
        this.quantity = quantity;
        this.location = location;
    }

    String getSku() {
        return sku;
    }

    int getQuantity() {
        return quantity;
    }

    String getLocation() {
        return location;
    }
}
