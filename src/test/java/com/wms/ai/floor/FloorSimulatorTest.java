package com.wms.ai.floor;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The simulator is enabled here (it is off by default everywhere else) with a deliberately
 * huge step delay, so the real scheduler never fires during the test and we drive
 * {@code tick()} by hand to observe each step deterministically. Isolated in-memory DB.
 *
 * <p>Seed reference (dev profile): SEED-ORD-1 = Acme/URGENT (SKU-1001 x2), WK-1 IDLE;
 * SEED-ORD-2 stays PENDING with no task.
 */
@SpringBootTest(
        properties = {
            "wms.floor.simulator.enabled=true",
            "wms.floor.simulator.step-delay-ms=3600000",
            "spring.datasource.url=jdbc:h2:mem:floorsimtest;DB_CLOSE_DELAY=-1"
        })
@ActiveProfiles("dev")
class FloorSimulatorTest {

    @Autowired
    FloorSimulator simulator;

    @Autowired
    DispatchService dispatch;

    @Autowired
    OrderService orders;

    @Autowired
    OutboundService outbound;

    @Test
    void tickWalksEveryInFlightPickForwardAndLeavesPendingOrdersUntouched() {
        dispatch.assignOrderToWorker("SEED-ORD-1", "WK-1"); // in-flight; SEED-ORD-2 stays PENDING

        simulator.tick();
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.PICKING);
        assertThat(orders.get("SEED-ORD-2").orElseThrow().status()).isEqualTo(OrderStatus.PENDING);

        simulator.tick();
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.PICKED);

        simulator.tick();
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(outbound.getWorker("WK-1").orElseThrow().status()).isEqualTo(WorkerStatus.IDLE);

        // A further tick has no in-flight work left to advance.
        simulator.tick();
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.SHIPPED);
    }
}
