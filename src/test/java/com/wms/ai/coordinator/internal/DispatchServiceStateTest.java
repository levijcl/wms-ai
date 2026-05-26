package com.wms.ai.coordinator.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.coordinator.WarehouseState;
import com.wms.ai.inventory.Stock;
import com.wms.ai.order.Order;
import com.wms.ai.outbound.Worker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies {@code warehouseState()} composes the three modules' reads into one
 * snapshot. Runs in the {@code dev} profile with an isolated in-memory DB so it sees
 * the seeded stocks/orders/workers and does not pollute the shared default-profile
 * database (mirrors the module seed-data tests).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:coordstatetest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class DispatchServiceStateTest {

    @Autowired
    DispatchService dispatch;

    @Test
    void warehouseStateReflectsSeededStocksOrdersAndWorkers() {
        WarehouseState state = dispatch.warehouseState();

        assertThat(state.stocks())
                .extracting(Stock::sku)
                .containsExactlyInAnyOrder(
                        "SKU-1001", "SKU-1002", "SKU-2001", "SKU-2002", "SKU-3001");
        assertThat(state.orders())
                .extracting(Order::id)
                .containsExactlyInAnyOrder(
                        "SEED-ORD-1", "SEED-ORD-2", "SEED-ORD-3", "SEED-ORD-4");
        assertThat(state.workers())
                .extracting(Worker::id)
                .containsExactlyInAnyOrder("WK-1", "WK-2", "WK-3", "WK-4", "WK-5", "WK-6");
    }

    @Test
    void warehouseStateHasNoTasksBeforeAnyDispatch() {
        // Picking tasks are created only by assignOrderToWorker, never seeded.
        assertThat(dispatch.warehouseState().tasks()).isEmpty();
    }
}
