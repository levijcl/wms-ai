package com.wms.ai.coordinator;

import com.wms.ai.inventory.Stock;
import com.wms.ai.order.Order;
import com.wms.ai.outbound.PickingTask;
import com.wms.ai.outbound.Worker;
import java.util.List;

/**
 * One read-only snapshot of the whole warehouse — the single payload the dispatch
 * console polls to render the map, order board, and picking-task list (README §3.6,
 * §7). Composed entirely of the business modules' existing public records, so no JPA
 * type ever leaks across the boundary.
 *
 * @param stocks  every SKU's current stock
 * @param orders  every order, in any lifecycle state
 * @param workers the whole labour pool, in any status
 * @param tasks   every picking task created so far
 */
public record WarehouseState(
        List<Stock> stocks, List<Order> orders, List<Worker> workers, List<PickingTask> tasks) {}
