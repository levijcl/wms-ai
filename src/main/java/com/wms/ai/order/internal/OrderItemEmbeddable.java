package com.wms.ai.order.internal;

import jakarta.persistence.Embeddable;

/**
 * JPA persistence model for one order line, owned by {@link OrderEntity} as an
 * {@code @ElementCollection} value (no independent identity). Package-private so
 * it never leaks past the module boundary — the impl maps it to the public
 * {@code OrderItem} record.
 */
@Embeddable
class OrderItemEmbeddable {

    private String sku;
    private int quantity;

    /** Required by JPA. */
    protected OrderItemEmbeddable() {}

    OrderItemEmbeddable(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }

    String getSku() {
        return sku;
    }

    int getQuantity() {
        return quantity;
    }
}
