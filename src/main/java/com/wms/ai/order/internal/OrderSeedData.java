package com.wms.ai.order.internal;

import com.wms.ai.order.OrderStatus;
import com.wms.ai.order.Priority;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds sample PENDING orders across priorities and zones so dispatch experiments
 * have work to dispatch. Items reference the inventory seed SKUs — including the
 * out-of-stock {@code SKU-2002}, so the "skip insufficient stock" path has a case
 * to hit.
 *
 * <p>Scoped to the {@code dev} profile and idempotent — it never runs under the
 * default test profile and never duplicates rows on restart.
 */
@Component
@Profile("dev")
class OrderSeedData implements CommandLineRunner {

    private final OrderRepository repository;

    OrderSeedData(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return; // already seeded — keep restarts idempotent
        }
        Instant now = Instant.now();
        repository.saveAll(List.of(
                order("SEED-ORD-1", "Acme", Priority.URGENT, now.plus(2, ChronoUnit.HOURS),
                        item("SKU-1001", 2)),
                order("SEED-ORD-2", "Globex", Priority.HIGH, now.plus(8, ChronoUnit.HOURS),
                        item("SKU-2001", 5)),
                order("SEED-ORD-3", "Initech", Priority.NORMAL, now.plus(24, ChronoUnit.HOURS),
                        item("SKU-1002", 1), item("SKU-3001", 3)),
                // needs the out-of-stock SKU-2002: exercises the skip-insufficient path
                order("SEED-ORD-4", "Umbrella", Priority.LOW, now.plus(48, ChronoUnit.HOURS),
                        item("SKU-2002", 1))));
    }

    private static OrderEntity order(
            String id, String customer, Priority priority, Instant dueAt, OrderItemEmbeddable... items) {
        return new OrderEntity(id, customer, List.of(items), priority, dueAt, OrderStatus.PENDING);
    }

    private static OrderItemEmbeddable item(String sku, int quantity) {
        return new OrderItemEmbeddable(sku, quantity);
    }
}
