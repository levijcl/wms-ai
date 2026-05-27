package com.wms.ai.coordinator.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.ai.coordinator.DispatchResult;
import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.TaskStatus;
import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the coordinator's second atomic composite, {@code advancePick} — the floor's
 * operator step that walks an assigned pick to its next milestone, coupling the order, task
 * and worker transitions. Runs against the seeded dev data on an isolated in-memory DB; the
 * floor simulator stays off (its enable property is unset), so progression here happens only
 * through explicit {@code advancePick} calls, not the scheduler.
 *
 * <p>Seed reference (dev profile): SEED-ORD-1 = Acme/URGENT, one line SKU-1001 x2; WK-1 IDLE.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:coordadvancetest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class DispatchServiceAdvancePickTest {

    @Autowired
    DispatchService dispatch;

    @Autowired
    OrderService orders;

    @Autowired
    OutboundService outbound;

    @Test
    void advancePickWalksTheCoupledLifecycleToShippedThenIsANoOp() {
        DispatchResult assigned = dispatch.assignOrderToWorker("SEED-ORD-1", "WK-1");
        String taskId = assigned.task().id();

        // 1) ASSIGNED → picking: task PICKING + order PICKING; worker stays BUSY.
        dispatch.advancePick(taskId);
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.PICKING);
        assertThat(outbound.getTask(taskId).orElseThrow().status()).isEqualTo(TaskStatus.PICKING);
        assertThat(outbound.getWorker("WK-1").orElseThrow().status()).isEqualTo(WorkerStatus.BUSY);

        // 2) PICKING → picked.
        dispatch.advancePick(taskId);
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.PICKED);

        // 3) PICKED → end: order SHIPPED, task DONE, worker freed to IDLE.
        dispatch.advancePick(taskId);
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(outbound.getTask(taskId).orElseThrow().status()).isEqualTo(TaskStatus.DONE);
        assertThat(outbound.getWorker("WK-1").orElseThrow().status()).isEqualTo(WorkerStatus.IDLE);

        // 4) Terminal: another call changes nothing and does not throw.
        dispatch.advancePick(taskId);
        assertThat(orders.get("SEED-ORD-1").orElseThrow().status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(outbound.getTask(taskId).orElseThrow().status()).isEqualTo(TaskStatus.DONE);
        assertThat(outbound.getWorker("WK-1").orElseThrow().status()).isEqualTo(WorkerStatus.IDLE);
    }

    @Test
    void advancePickOnUnknownTaskThrowsIllegalArgument() {
        assertThatThrownBy(() -> dispatch.advancePick("NO-SUCH-TASK"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
