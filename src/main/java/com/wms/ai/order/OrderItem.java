package com.wms.ai.order;

/**
 * One line of an order: a quantity of a SKU to be picked. Immutable public view
 * — part of the Order module's sealed port. The JPA persistence model
 * ({@code OrderItemEmbeddable}) is package-private under {@code internal}.
 *
 * @param sku      stock-keeping unit identifier (matches Inventory's SKU)
 * @param quantity units requested (always positive — validated on submit)
 */
public record OrderItem(String sku, int quantity) {}
