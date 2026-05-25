package com.wms.ai.inventory.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link StockEntity}. Package-private — hidden
 * behind {@code InventoryService} so an H2 → Postgres swap needs no caller
 * changes.
 */
interface StockRepository extends JpaRepository<StockEntity, String> {}
