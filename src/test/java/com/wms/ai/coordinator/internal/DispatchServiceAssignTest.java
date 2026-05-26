package com.wms.ai.coordinator.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.ai.coordinator.DispatchResult;
import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.inventory.InventoryService;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.PickingTask;
import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the atomic composite {@code assignOrderToWorker} against the seeded dev
 * data: the happy path and every guardrail-failure rollback. Runs unmanaged (the test
 * itself is not transactional) so each guardrail rollback is observed as committed
 * state, not as the test's own transaction unwinding. Tests use disjoint seed orders
 * and workers and assert relative to a pre-call read, so they are order-independent in
 * the shared in-memory DB.
 *
 * <p>Seed reference (dev profile): stocks SKU-1001=100(Z1), SKU-1002=50(Z1),
 * SKU-2001=75(Z2), SKU-2002=0(Z2), SKU-3001=25(Z3); orders SEED-ORD-1..4 all PENDING;
 * workers WK-1..4 IDLE, WK-5 BUSY, WK-6 OFFLINE.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:coordassigntest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class DispatchServiceAssignTest {

    @Autowired
    DispatchService dispatch;

    @Autowired
    InventoryService inventory;

    @Autowired
    OrderService orders;

    @Autowired
    OutboundService outbound;

    @Test
    void happyPathReservesStockAdvancesOrderAndWorkerAndCreatesTask() {
        // SEED-ORD-1 = Acme/URGENT, one line SKU-1001 x2; assign to idle WK-1.
        int stockBefore = inventory.getStock("SKU-1001").orElseThrow().quantity();

        DispatchResult result = dispatch.assignOrderToWorker("SEED-ORD-1", "WK-1");

        assertThat(inventory.getStock("SKU-1001").orElseThrow().quantity())
                .isEqualTo(stockBefore - 2);
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status())
                .isEqualTo(OrderStatus.ASSIGNED);
        assertThat(outbound.getWorker("WK-1").orElseThrow().status())
                .isEqualTo(WorkerStatus.BUSY);

        PickingTask task = result.task();
        assertThat(result.orderId()).isEqualTo("SEED-ORD-1");
        assertThat(result.workerId()).isEqualTo("WK-1");
        assertThat(task.orderId()).isEqualTo("SEED-ORD-1");
        assertThat(task.workerId()).isEqualTo("WK-1");
        // The task is persisted and visible in the aggregate snapshot.
        assertThat(outbound.getTask(task.id())).isPresent();
        assertThat(dispatch.warehouseState().tasks()).extracting(PickingTask::id).contains(task.id());
    }

    @Test
    void unknownOrderThrowsIllegalArgumentAndCreatesNoTask() {
        assertThatThrownBy(() -> dispatch.assignOrderToWorker("NO-SUCH-ORDER", "WK-3"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(outbound.getWorker("WK-3").orElseThrow().status())
                .isEqualTo(WorkerStatus.IDLE);
        assertThat(outbound.listTasks())
                .noneMatch(t -> t.orderId().equals("NO-SUCH-ORDER"));
    }

    @Test
    void insufficientStockThrowsIllegalStateAndLeavesEverythingUnchanged() {
        // SEED-ORD-4 needs SKU-2002, which is seeded at 0.
        int stockBefore = inventory.getStock("SKU-2002").orElseThrow().quantity();

        assertThatThrownBy(() -> dispatch.assignOrderToWorker("SEED-ORD-4", "WK-2"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(inventory.getStock("SKU-2002").orElseThrow().quantity()).isEqualTo(stockBefore);
        assertThat(orders.get("SEED-ORD-4").orElseThrow().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(outbound.getWorker("WK-2").orElseThrow().status()).isEqualTo(WorkerStatus.IDLE);
        assertThat(outbound.listTasks()).noneMatch(t -> t.orderId().equals("SEED-ORD-4"));
    }

    @Test
    void nonPendingOrderThrowsIllegalStateAndRollsBackTheReserve() {
        // Push SEED-ORD-3 out of PENDING first; the assign must then reject it and
        // undo the reserves it had already made for its two lines.
        orders.updateStatus("SEED-ORD-3", OrderStatus.ASSIGNED);
        int sku1002Before = inventory.getStock("SKU-1002").orElseThrow().quantity();
        int sku3001Before = inventory.getStock("SKU-3001").orElseThrow().quantity();

        assertThatThrownBy(() -> dispatch.assignOrderToWorker("SEED-ORD-3", "WK-4"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(inventory.getStock("SKU-1002").orElseThrow().quantity()).isEqualTo(sku1002Before);
        assertThat(inventory.getStock("SKU-3001").orElseThrow().quantity()).isEqualTo(sku3001Before);
        assertThat(outbound.getWorker("WK-4").orElseThrow().status()).isEqualTo(WorkerStatus.IDLE);
        assertThat(outbound.listTasks()).noneMatch(t -> t.orderId().equals("SEED-ORD-3"));
    }

    @Test
    void racedNonIdleWorkerThrowsIllegalStateAndRollsBackStockAndOrder() {
        // WK-5 is seeded BUSY: the worker step fails after the reserve + order step,
        // so the whole transaction — including the reserve — must roll back.
        int stockBefore = inventory.getStock("SKU-2001").orElseThrow().quantity();

        assertThatThrownBy(() -> dispatch.assignOrderToWorker("SEED-ORD-2", "WK-5"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(inventory.getStock("SKU-2001").orElseThrow().quantity()).isEqualTo(stockBefore);
        assertThat(orders.get("SEED-ORD-2").orElseThrow().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(outbound.getWorker("WK-5").orElseThrow().status()).isEqualTo(WorkerStatus.BUSY);
        assertThat(outbound.listTasks()).noneMatch(t -> t.orderId().equals("SEED-ORD-2"));
    }
}
