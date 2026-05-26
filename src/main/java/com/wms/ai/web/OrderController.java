package com.wms.ai.web;

import com.wms.ai.order.NewOrder;
import com.wms.ai.order.Order;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import com.wms.ai.web.dto.StatusRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order command endpoints: submit a new order and advance an order's status. {@code
 * NewOrder} binds directly from the request body; the status string is converted to
 * {@link OrderStatus} here so an unknown value becomes a clean 400. All validation and
 * the transition guardrail live in {@link OrderService}.
 */
@RestController
@RequestMapping("/api")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/orders")
    Order submit(@RequestBody NewOrder draft) {
        return orders.submit(draft);
    }

    @PostMapping("/orders/{id}/status")
    Order updateStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return orders.updateStatus(id, OrderStatus.valueOf(request.status()));
    }
}
