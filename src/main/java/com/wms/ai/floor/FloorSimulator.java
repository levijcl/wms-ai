package com.wms.ai.floor;

import com.wms.ai.coordinator.DispatchService;
import com.wms.ai.coordinator.WarehouseState;
import com.wms.ai.order.Order;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.outbound.PickingTask;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The stand-in for the warehouse operator. There is no worker-facing UI (a non-goal,
 * README §1), so once the planner assigns a pick something must execute it — moving the
 * order/task/worker through {@code PICKING → PICKED → SHIPPED}. This simulator does that
 * by ticking {@link DispatchService#advancePick} for every in-flight pick on a fixed
 * cadence, so progression happens server-side regardless of whether a console is open —
 * which also lets a headless Phase B (AI) run advance to completion.
 *
 * <p>It is a driver on top of the coordinator, depending only on the public
 * {@link DispatchService} port. Gated by {@code wms.floor.simulator.enabled} (default
 * <strong>off</strong>): the live console run opts in, while every test leaves it unset so
 * the scheduler never mutates state mid-test.
 */
@Component
@ConditionalOnProperty(name = "wms.floor.simulator.enabled", havingValue = "true")
class FloorSimulator {

    /** Orders whose pick is still being executed — the ones a tick should advance. */
    private static final Set<OrderStatus> IN_FLIGHT =
            EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.PICKING, OrderStatus.PICKED);

    private final DispatchService dispatch;

    FloorSimulator(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * Advance every in-flight pick one step. Fired on a fixed cadence; {@code initialDelay}
     * matches so the first tick is one period out (keeps it deterministic in tests, which set
     * a very large delay and drive {@code tick()} by hand).
     */
    @Scheduled(
            fixedDelayString = "${wms.floor.simulator.step-delay-ms:2000}",
            initialDelayString = "${wms.floor.simulator.step-delay-ms:2000}")
    public void tick() {
        WarehouseState state = dispatch.warehouseState();
        Map<String, OrderStatus> statusByOrder =
                state.orders().stream().collect(Collectors.toMap(Order::id, Order::status));
        for (PickingTask task : state.tasks()) {
            if (IN_FLIGHT.contains(statusByOrder.get(task.orderId()))) {
                dispatch.advancePick(task.id());
            }
        }
    }
}
