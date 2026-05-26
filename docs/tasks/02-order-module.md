# Implementation Plan: Order Module (Module 2)

> Second of the three business modules in WMS-AI. See `README.md` §3.2 for the design.
> Inventory (Module 1, `01-inventory-module.md`) is complete and established the pattern this
> module follows. The checkbox task list below doubles as the todo list.

## Context

The Inventory module (Module 1) is complete and established the project-wide **feature +
sealed port** pattern: one public port interface + immutable view records in the feature
package, with the JPA entity, repository, `@Service` impl, and seed data package-private
under `internal/`; tests use `@DataJpaTest` + `@Import(Impl.class)`; an ArchUnit rule guards
the boundary.

The Order module manages the **order lifecycle** — it is the system's record of what needs
to be picked and what state each order is in. The AI coordinator (Module 3, later) reads
pending orders and drives them through their states; this module just stores orders and
**enforces that state transitions are legal**. It is a sibling leaf to Inventory: per
README §3.2 it must **not** mutate stock or assign workers, and therefore has **zero
dependency on the Inventory module** — the future AI coordinator wires the two together.

Three things are new versus Inventory and shape the plan:

1. **A collection of items** per order (`OrderItem[]`) — a JPA `@ElementCollection`.
2. **A state machine** (`PENDING → ASSIGNED → PICKING → PICKED → SHIPPED`, or `CANCELLED`)
   whose transition rules are the module's core guardrail.
3. **An order-creation path** (`submit`) — README's responsibility list ("accept and
   persist incoming orders") requires it even though §3.2's explicit API list omits it.

## Confirmed decisions

- **`submit(NewOrder)` is added to the port**, returning the created `Order`. No REST
  controller this iteration (consistent with Inventory) — orders enter via `submit`, called
  by seed data and tests now, and by the AI/ingest layer later.
- **IDs are service-generated UUID strings** (`Order.id` is a `String`). Orders have no
  natural key, unlike `Stock` (keyed by SKU).
- **Cancellable any time before SHIPPED.** Legal transitions:
  | From | Allowed → |
  |------|-----------|
  | `PENDING` | `ASSIGNED`, `CANCELLED` |
  | `ASSIGNED` | `PICKING`, `CANCELLED` |
  | `PICKING` | `PICKED`, `CANCELLED` |
  | `PICKED` | `SHIPPED`, `CANCELLED` |
  | `SHIPPED` | — (terminal) |
  | `CANCELLED` | — (terminal) |
  Illegal transition → `IllegalStateException`; unknown id → `IllegalArgumentException`.

## Module layout (mirrors Inventory)

```
com.wms.ai.order
  OrderService              public interface — the only entry point (the "port")
  Order                     public record (id, customer, items, priority, dueAt, status) — the view
  OrderItem                 public record (sku, quantity)
  NewOrder                  public record (customer, items, priority, dueAt) — submit() input, no id/status
  Priority                  public enum LOW | NORMAL | HIGH | URGENT
  OrderStatus               public enum PENDING | ASSIGNED | PICKING | PICKED | SHIPPED | CANCELLED
  internal/
    OrderEntity             package-private @Entity; items via @ElementCollection
    OrderItemEmbeddable     package-private @Embeddable (sku, quantity)
    OrderRepository         package-private JpaRepository<OrderEntity, String> + findByStatus
    OrderServiceImpl        package-private @Service implementing the port
    OrderSeedData           package-private @Profile("dev") CommandLineRunner
```

Port surface:

```java
public interface OrderService {
    Order submit(NewOrder draft);                         // assigns UUID id, status = PENDING
    Optional<Order> get(String id);                        // empty for unknown id
    List<Order> listByStatus(OrderStatus status);
    List<Order> listAll();
    Order updateStatus(String id, OrderStatus newStatus);  // validates the transition
}
```

## Architecture decisions

- **Sealed port.** Public types are the port + the view/input records + the two enums. The
  JPA `@Entity` (`OrderEntity`) and `@Embeddable` (`OrderItemEmbeddable`) stay package-private
  in `internal/`; the impl maps `OrderEntity → Order` / `OrderItemEmbeddable → OrderItem` so
  JPA types never cross the boundary — same discipline as `StockEntity → Stock`.
- **Items as `@ElementCollection`** of an `@Embeddable`, owned by the order (value objects,
  no independent identity). Default fetch is **LAZY**, and `open-in-view: false` is already
  set — so **read methods must be `@Transactional(readOnly = true)`** and map to records
  inside the transaction, or they will throw `LazyInitializationException`. This is the main
  new pitfall versus Inventory's flat entity.
- **Enums persisted as `@Enumerated(EnumType.STRING)`** — stable across reordering, readable
  in the H2 console.
- **State machine lives in the service layer** (a transition table in `OrderServiceImpl`),
  not on the public enum — guardrails belong in the business layer per README §2, and
  keeping it out of the enum avoids leaking policy into the public surface.
- **No dependency on Inventory.** Order neither imports nor calls Inventory; the two are
  independent leaves wired only by the future AI coordinator.

## Task list

### Phase 1 — Foundation

#### Task 1: Port + view/input types + entity + repository (sealed skeleton)
**Description:** Create the module in the sealed-port shape — public port, view records,
input record, and enums; package-private entity (with the `@ElementCollection` items
mapping), embeddable, and repository.

**Acceptance criteria:**
- [ ] Public `OrderService`, `Order`, `OrderItem`, `NewOrder`, `Priority`, `OrderStatus` in `com.wms.ai.order`.
- [ ] Package-private `OrderEntity` (`@Id String id`; `customer`; `priority`/`status` as `@Enumerated(STRING)`; `dueAt` as `Instant`; `@ElementCollection List<OrderItemEmbeddable> items`), `OrderItemEmbeddable`, and `OrderRepository extends JpaRepository<OrderEntity, String>` with `List<OrderEntity> findByStatus(OrderStatus)`, all in `com.wms.ai.order.internal`.

**Verification:**
- [ ] `@DataJpaTest`: save an `OrderEntity` with **two** items → `findById` returns it with both items intact (proves the collection-table mapping round-trips).
- [ ] `./gradlew test` green.

**Dependencies:** None
**Files likely touched:** `order/OrderService.java`, `order/Order.java`, `order/OrderItem.java`, `order/NewOrder.java`, `order/Priority.java`, `order/OrderStatus.java`, `order/internal/OrderEntity.java`, `order/internal/OrderItemEmbeddable.java`, `order/internal/OrderRepository.java`, `src/test/.../order/internal/OrderRepositoryTest.java`
**Estimated scope:** M

> **Checkpoint — after Task 1:** app boots with both modules; an order with items persists and reloads; tests green.

### Phase 2 — Service API

#### Task 2: `submit(NewOrder)` — accept & persist as PENDING
**Description:** Implement the creation path. Validate the draft, generate a UUID id, default
status to `PENDING`, persist, and map back to the `Order` view.

**Acceptance criteria:**
- [ ] `@Service` `OrderServiceImpl` in `internal`; `submit` returns an `Order` with a non-null generated id, `status == PENDING`, and the submitted items/priority/dueAt echoed.
- [ ] Draft validation → `IllegalArgumentException`: null/blank customer, null/empty items, any item with `quantity <= 0` or blank sku, null priority, null `dueAt`.

**Verification:**
- [ ] Test: `submit` then `get(id)` returns an equal order; each invalid draft throws.

**Dependencies:** Task 1
**Files likely touched:** `order/internal/OrderServiceImpl.java`, `src/test/.../order/internal/OrderServiceSubmitTest.java`
**Estimated scope:** S

#### Task 3: Read operations — `get(id)`, `listByStatus(status)`, `listAll()`
**Description:** Implement the read side, mapping entities (and their lazy item collections)
to records inside a read-only transaction.

**Acceptance criteria:**
- [ ] Read methods are `@Transactional(readOnly = true)`; `get` → `Optional<Order>` (empty for unknown id); `listByStatus` filters via `OrderRepository.findByStatus`; `listAll` returns all.
- [ ] `OrderEntity → Order` mapping (incl. items) done in the impl; no entity escapes the port; no `LazyInitializationException`.

**Verification:**
- [ ] Test with orders of mixed statuses: known id present / unknown empty; `listByStatus` returns only matching; `listAll` returns all; items populated on every returned `Order`.

**Dependencies:** Task 1 (Task 2 recommended, to create fixtures via the port)
**Files likely touched:** `order/internal/OrderServiceImpl.java`, `src/test/.../order/internal/OrderServiceReadTest.java`
**Estimated scope:** S

#### Task 4: `updateStatus(id, newStatus)` — state-machine guardrail
**Description:** Implement the transition table and enforce it. This is the module's core
guardrail and the precondition the AI coordinator's `assignOrderToWorker` relies on.

**Acceptance criteria:**
- [ ] Transition table per "Confirmed decisions"; a legal transition persists the new status and returns the updated `Order`.
- [ ] Illegal transition (incl. any move out of terminal `SHIPPED`/`CANCELLED`) → `IllegalStateException`; unknown id → `IllegalArgumentException`.

**Verification:**
- [ ] Parameterized tests covering each legal transition (succeeds) and a representative set of illegal ones + terminal-state moves (throw); unknown id throws.

**Dependencies:** Task 1, Task 3
**Files likely touched:** `order/internal/OrderServiceImpl.java`, `src/test/.../order/internal/OrderServiceStatusTransitionTest.java`
**Estimated scope:** S–M

> **Checkpoint — after Task 4:** full Order API works; illegal transitions are rejected; tests green.

### Phase 3 — Seed & polish

#### Task 5: Dev-profile seed data
**Description:** Seed sample orders so later dispatch experiments have data — varied
priorities and due times, items referencing the inventory seed SKUs/zones (incl. one order
that needs the out-of-stock `SKU-2002` to exercise the "skip insufficient stock" path).

**Acceptance criteria:**
- [ ] Package-private `OrderSeedData` `@Profile("dev")` `CommandLineRunner`, idempotent (`count() > 0` guard), seeding several `PENDING` orders across priorities/zones, referencing existing seed SKUs.
- [ ] Does not run under the default test profile; does not duplicate on restart.

**Verification:**
- [ ] Test mirroring `SeedDataTest`, or `./gradlew bootRun --args='--spring.profiles.active=dev'` → `listAll()` returns the seeded set.

**Dependencies:** Task 1, Task 2
**Files likely touched:** `order/internal/OrderSeedData.java`, `src/test/.../order/internal/OrderSeedDataTest.java`
**Estimated scope:** S

#### Task 6: Extend the ArchUnit boundary guard to `order.internal`
**Description:** Add a rule so nothing outside the Order module reaches into
`com.wms.ai.order.internal`, locking the sealed-port pattern for Module 2 as it is for
Inventory.

**Acceptance criteria:**
- [ ] `ArchitectureTest` asserts no type outside `com.wms.ai.order..` depends on `com.wms.ai.order.internal..` (mirror the existing inventory rule, or generalize both into one parameterized rule over module packages).

**Verification:**
- [ ] `./gradlew test` — the rule passes against the current code.

**Dependencies:** Task 1
**Files likely touched:** `src/test/java/com/wms/ai/ArchitectureTest.java`
**Estimated scope:** S

> **Checkpoint — Complete:** all AC met; `./gradlew test` green; module exposes only
> `OrderService` + `Order`/`OrderItem`/`NewOrder` + `Priority`/`OrderStatus`; no AI imports;
> no dependency on Inventory; internals don't leak.

## Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Lazy `@ElementCollection` mapped outside a session → `LazyInitializationException` | High | `@Transactional(readOnly = true)` read methods; map inside the tx; `open-in-view` stays false (Task 3 verifies items populate) |
| State-machine drift / a missing or wrong transition | High (bad order state) | Explicit transition table + parameterized tests over **all** legal and representative illegal moves (Task 4) |
| Enum persisted as ordinal → brittle on reorder | Med | `@Enumerated(EnumType.STRING)` |
| Accidental coupling to Inventory | Med (breaks unidirectional design) | No Inventory import/call in Order; ArchUnit + review |

## Verification (end to end)

- `./gradlew test` — all module tests green (repository round-trip, submit, reads, transitions, seed, ArchUnit).
- `./gradlew bootRun --args='--spring.profiles.active=dev'` — context loads both modules; seed runs; H2 console at `/h2-console` shows the `ORDER_ENTITY` + item collection tables.
- Boundary: confirm no production type outside `com.wms.ai.order` references `com.wms.ai.order.internal` (ArchUnit), and the module has no `com.wms.ai.inventory.internal` or AI references.
