package com.wms.ai.order.internal;

import com.wms.ai.order.NewOrder;
import com.wms.ai.order.Order;
import com.wms.ai.order.OrderItem;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the Order port. Maps the package-private
 * {@link OrderEntity} / {@link OrderItemEmbeddable} to the public {@code Order} /
 * {@code OrderItem} records so JPA types never cross the module boundary.
 */
@Service
class OrderServiceImpl implements OrderService {

    private final OrderRepository repository;

    OrderServiceImpl(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Order submit(NewOrder draft) {
        validate(draft);
        var entity = new OrderEntity(
                UUID.randomUUID().toString(),
                draft.customer(),
                draft.items().stream()
                        .map(item -> new OrderItemEmbeddable(item.sku(), item.quantity()))
                        .toList(),
                draft.priority(),
                draft.dueAt(),
                OrderStatus.PENDING);
        return toOrder(repository.save(entity));
    }

    @Override
    public Optional<Order> get(String id) {
        throw new UnsupportedOperationException("implemented in Task 3");
    }

    @Override
    public List<Order> listByStatus(OrderStatus status) {
        throw new UnsupportedOperationException("implemented in Task 3");
    }

    @Override
    public List<Order> listAll() {
        throw new UnsupportedOperationException("implemented in Task 3");
    }

    @Override
    public Order updateStatus(String id, OrderStatus newStatus) {
        throw new UnsupportedOperationException("implemented in Task 4");
    }

    private static void validate(NewOrder draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        if (draft.customer() == null || draft.customer().isBlank()) {
            throw new IllegalArgumentException("customer must not be blank");
        }
        if (draft.items() == null || draft.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        for (OrderItem item : draft.items()) {
            if (item == null) {
                throw new IllegalArgumentException("item must not be null");
            }
            if (item.sku() == null || item.sku().isBlank()) {
                throw new IllegalArgumentException("item sku must not be blank");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("item quantity must be positive: " + item.quantity());
            }
        }
        if (draft.priority() == null) {
            throw new IllegalArgumentException("priority must not be null");
        }
        if (draft.dueAt() == null) {
            throw new IllegalArgumentException("dueAt must not be null");
        }
    }

    private static Order toOrder(OrderEntity entity) {
        var items = entity.getItems().stream()
                .map(item -> new OrderItem(item.getSku(), item.getQuantity()))
                .toList();
        return new Order(
                entity.getId(),
                entity.getCustomer(),
                items,
                entity.getPriority(),
                entity.getDueAt(),
                entity.getStatus());
    }
}
