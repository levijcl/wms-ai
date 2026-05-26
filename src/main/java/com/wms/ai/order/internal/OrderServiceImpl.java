package com.wms.ai.order.internal;

import com.wms.ai.order.NewOrder;
import com.wms.ai.order.Order;
import com.wms.ai.order.OrderItem;
import com.wms.ai.order.OrderService;
import com.wms.ai.order.OrderStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Legal next states per current status. Forward path
     * {@code PENDING → ASSIGNED → PICKING → PICKED → SHIPPED}; {@code CANCELLED}
     * reachable from any non-terminal state; {@code SHIPPED} and {@code CANCELLED}
     * are terminal. This is the module's core guardrail.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED),
            OrderStatus.ASSIGNED, EnumSet.of(OrderStatus.PICKING, OrderStatus.CANCELLED),
            OrderStatus.PICKING, EnumSet.of(OrderStatus.PICKED, OrderStatus.CANCELLED),
            OrderStatus.PICKED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

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

    // Reads are @Transactional(readOnly = true) so the lazy item collection is
    // mapped to records while the session is open — open-in-view is off.

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> get(String id) {
        return repository.findById(id).map(OrderServiceImpl::toOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> listByStatus(OrderStatus status) {
        return repository.findByStatus(status).stream().map(OrderServiceImpl::toOrder).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> listAll() {
        return repository.findAll().stream().map(OrderServiceImpl::toOrder).toList();
    }

    @Override
    @Transactional
    public Order updateStatus(String id, OrderStatus newStatus) {
        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown order: " + id));
        var current = entity.getStatus();
        if (!ALLOWED_TRANSITIONS.get(current).contains(newStatus)) {
            throw new IllegalStateException("illegal transition: " + current + " -> " + newStatus);
        }
        entity.setStatus(newStatus);
        return toOrder(repository.save(entity));
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
