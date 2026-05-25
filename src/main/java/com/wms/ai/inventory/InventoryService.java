package com.wms.ai.inventory;

import java.util.List;
import java.util.Optional;

/**
 * The Inventory module's single public entry point — its port.
 *
 * <p>Source of truth for SKU stock. Guardrails (stock sufficiency, valid
 * quantities) live here in the service layer, never in a caller or a prompt.
 * Callers depend only on this interface and {@link Stock}; the JPA entity,
 * repository, and implementation are package-private under {@code internal}.
 */
public interface InventoryService {

    /** Current stock for a SKU, or empty if the SKU is unknown. */
    Optional<Stock> getStock(String sku);

    /** All stock rows. */
    List<Stock> listAll();

    /**
     * Atomically reserve {@code quantity} units of {@code sku}.
     *
     * @return {@code true} iff the stock was decremented; {@code false} when the
     *         SKU is unknown or has insufficient stock.
     * @throws IllegalArgumentException if {@code quantity <= 0}
     */
    boolean reserve(String sku, int quantity);

    /**
     * Restock {@code quantity} units of {@code sku} — used on picking failure or
     * order cancellation.
     *
     * @throws IllegalArgumentException if {@code quantity <= 0}
     */
    void release(String sku, int quantity);
}
