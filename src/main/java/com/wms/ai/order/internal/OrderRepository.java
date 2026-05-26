package com.wms.ai.order.internal;

import com.wms.ai.order.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link OrderEntity}. Package-private — hidden behind
 * {@code OrderService} so an H2 → Postgres swap needs no caller changes.
 */
interface OrderRepository extends JpaRepository<OrderEntity, String> {

    /** All orders currently in the given status (derived query). */
    List<OrderEntity> findByStatus(OrderStatus status);
}
