package com.wms.ai.inventory.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link StockEntity}. Package-private — hidden
 * behind {@code InventoryService} so an H2 → Postgres swap needs no caller
 * changes.
 */
interface StockRepository extends JpaRepository<StockEntity, String> {

    /**
     * Atomic, race-safe reservation: a single conditional UPDATE that decrements
     * only when enough stock remains. The {@code quantity >= :qty} guard makes
     * oversell impossible under concurrency without explicit locks — the DB
     * serializes the row write.
     *
     * @return rows affected — {@code 1} on success, {@code 0} when the SKU is
     *         unknown or has insufficient stock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StockEntity s SET s.quantity = s.quantity - :qty "
            + "WHERE s.sku = :sku AND s.quantity >= :qty")
    int reserve(@Param("sku") String sku, @Param("qty") int qty);
}
