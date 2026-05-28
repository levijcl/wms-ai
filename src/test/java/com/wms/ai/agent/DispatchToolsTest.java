package com.wms.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.order.Order;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.Worker;
import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the AI's tools directly — no LLM involved. Against the seeded dev data on an
 * isolated in-memory DB (simulator off, property unset). Assertions are order-independent:
 * the only successful mutation is the happy-path assign (SEED-ORD-1 / WK-1 / SKU-1001), so
 * the read-tool tests assert on entities no test mutates.
 *
 * <p>Seed reference (dev): stocks SKU-1001=100(Z1), SKU-1002=50(Z1), SKU-2001=75(Z2),
 * SKU-2002=0(Z2), SKU-3001=25(Z3); orders SEED-ORD-1..4 PENDING; WK-1..4 IDLE, WK-5 BUSY,
 * WK-6 OFFLINE.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agenttoolstest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class DispatchToolsTest {

    @Autowired
    DispatchTools tools;

    @Autowired
    OrderService orders;

    @Autowired
    OutboundService outbound;

    @Test
    void listPendingOrdersReturnsThePendingSeedOrders() {
        assertThat(tools.listPendingOrders())
                .allMatch(o -> o.status() == OrderStatus.PENDING)
                .extracting(Order::id)
                .contains("SEED-ORD-2", "SEED-ORD-3", "SEED-ORD-4"); // never successfully assigned
    }

    @Test
    void getStockReturnsTheSkuStockOrNullWhenUnknown() {
        assertThat(tools.getStock("SKU-3001").quantity()).isEqualTo(25); // never reserved by any test
        assertThat(tools.getStock("NO-SUCH-SKU")).isNull();
    }

    @Test
    void listAvailableWorkersReturnsIdleWorkersOnly() {
        assertThat(tools.listAvailableWorkers())
                .allMatch(w -> w.status() == WorkerStatus.IDLE)
                .extracting(Worker::id)
                .contains("WK-4") // never assigned by any test
                .doesNotContain("WK-5", "WK-6"); // BUSY / OFFLINE
    }

    @Test
    void assignToolPerformsTheAssignmentOnTheHappyPath() {
        AssignmentOutcome outcome = tools.assignOrderToWorker("SEED-ORD-1", "WK-1");

        assertThat(outcome.assigned()).isTrue();
        assertThat(outcome.detail()).isNotBlank(); // the created task id
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(outbound.getWorker("WK-1").orElseThrow().status()).isEqualTo(WorkerStatus.BUSY);
    }

    @Test
    void assignToolReportsInsufficientStockAsASkipWithoutThrowingOrCorruptingState() {
        AssignmentOutcome outcome = tools.assignOrderToWorker("SEED-ORD-4", "WK-2"); // needs SKU-2002=0

        assertThat(outcome.assigned()).isFalse();
        assertThat(outcome.detail()).contains("insufficient stock for SKU-2002");
        assertThat(orders.get("SEED-ORD-4").orElseThrow().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(outbound.getWorker("WK-2").orElseThrow().status()).isEqualTo(WorkerStatus.IDLE);
    }

    @Test
    void assignToolReportsUnknownOrderAsASkipWithoutThrowing() {
        AssignmentOutcome outcome = tools.assignOrderToWorker("NO-SUCH-ORDER", "WK-3");

        assertThat(outcome.assigned()).isFalse();
        assertThat(outcome.detail()).isNotBlank();
        assertThat(outbound.getWorker("WK-3").orElseThrow().status()).isEqualTo(WorkerStatus.IDLE);
    }
}
