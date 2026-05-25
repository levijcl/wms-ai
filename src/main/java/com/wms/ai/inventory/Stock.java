package com.wms.ai.inventory;

/**
 * Immutable public view of a SKU's stock. This is part of the module's sealed
 * port: callers see {@code Stock}, never the JPA {@code StockEntity}.
 *
 * @param sku      stock-keeping unit identifier
 * @param quantity units currently available
 * @param location storage zone (e.g. {@code ZONE-1})
 */
public record Stock(String sku, int quantity, String location) {}
