# Implementation Plan: Coordinator Module (Module 4)

> The **pluggable dispatch seam**. See `README.md` §3.4 for the design. The three business
> modules — Inventory (`01-inventory-module.md`), Order (`02-order-module.md`), Outbound
> (`03-outbound-module.md`) — are complete. This module is the first thing to sit *above* them:
> it is the single place that wires all three together, and the seam that a human (via the web
> layer, `05-web-api.md`) drives now and the AI module (README §3.7) drives later. The checkbox
> task list below doubles as the todo list.

## Context

The three business modules established the project-wide **feature + sealed port** pattern and are
deliberately ignorant of each other and of anything above them. Cross-module coordination was
always "the dispatcher's job" — but rather than bake that job into the AI module, we extract it
into a **dispatcher-agnostic coordinator** so the human console and the AI plug into the *same*
seam (README §1, §2).

The coordinator is unlike the three business modules in one structural way: **it owns no
entity.** It is pure orchestration over the three public ports (`InventoryService`,
`OrderService`, `OutboundService`), injected by constructor. It still follows the sealed-port
shape — a public port + public view records, with the `@Service` impl package-private under
`internal/` — so the boundary discipline and ArchUnit guard carry over unchanged.

Two things shape the plan:

1. **The atomic composite.** `assignOrderToWorker` performs "reserve stock + Order → ASSIGNED +
   Worker → BUSY + create task" as one unit. It must be **`@Transactional`** so a guardrail
   failure at any step rolls back everything already applied (README §6). This is the whole
   reason the composite lives in one place instead of being duplicated in every caller.
2. **The aggregated read.** `warehouseState()` composes the three modules' reads into a single
   snapshot record — the one payload the visualization polls (README §3.6, §7). It is the only
   read the UI needs for the map + board + task list.

## Confirmed decisions

- **One port, `DispatchService`, no entity.** Public surface is the port + two view records
  (`WarehouseState`, `DispatchResult`). The impl is package-private `DispatchServiceImpl` in
  `coordinator/internal/`. No `@Entity`, no repository — it delegates entirely to the three
  injected ports.
- **`assignOrderToWorker(orderId, workerId)` is the atomic composite**, in this order:
  1. `Inventory.reserve(sku, qty)` for **every** item line of the order. `reserve` returns a
     **boolean**, not an exception — so on `false`, the impl throws `IllegalStateException`
     ("insufficient stock for <sku>") to abort and roll back.
  2. `Order.updateStatus(orderId, ASSIGNED)` — rejects a non-PENDING order (`IllegalStateException`).
  3. `Outbound.updateWorkerStatus(workerId, BUSY)` — rejects a non-IDLE/raced worker (`IllegalStateException`).
  4. `Outbound.createTask(orderId, workerId)` — returns the new `PickingTask`.
  Wrapped in `@Transactional`, so any failure undoes the reserves and leaves order/worker
  untouched. The order is **read first** (to get its item lines and confirm it exists) — unknown
  id → `IllegalArgumentException`.
- **Do not relax any check.** The coordinator surfaces the modules' exceptions as-is; it never
  catches-and-continues or skips a guardrail to force a "successful" assignment (README §6).
- **`warehouseState()` is read-only aggregation:** `OrderService.listAll()`,
  `InventoryService.listAll()`, all workers (union of `listWorkersByStatus` over the three
  statuses), and all tasks. Returns a `WarehouseState` record of the four lists. No mutation.
- **No new dependency direction is introduced.** The coordinator → business modules edge is the
  intended "up calls down" direction; the business modules gain nothing and still import neither
  each other nor the coordinator.
- **Reads of workers/tasks:** Outbound has no `listAll` for workers or tasks today. Either add a
  small read to `OutboundService` (preferred, see Task 2) or union the per-status lists in the
  coordinator. Decide in Task 2 and keep it consistent.

## Module layout (mirrors the sealed-port pattern, minus the entity)

```
com.wms.ai.coordinator
  DispatchService           public interface — the only entry point (the "port")
  WarehouseState            public record (List<Stock>, List<Order>, List<Worker>, List<PickingTask>) — the UI snapshot
  DispatchResult            public record (PickingTask task, String orderId, String workerId) — the assign outcome
  internal/
    DispatchServiceImpl     package-private @Service implementing the port; injects the three module ports
```

Port surface:

```java
public interface DispatchService {
    WarehouseState warehouseState();                                       // read-only aggregate snapshot
    DispatchResult assignOrderToWorker(String orderId, String workerId);   // atomic composite; @Transactional in the impl
}
```

## Architecture decisions

- **Sealed port, no entity.** The coordinator has no persistence of its own; the impl stays
  package-private in `internal/` purely to keep the public surface to the port + records, exactly
  like the three business modules.
- **`@Transactional` composite is the point.** Because all three modules share one H2 datasource
  and Spring's transaction manager, a `@Transactional` on `assignOrderToWorker` makes the
  `reserve` UPDATE and the order/worker mutations join **one** transaction — so a later failure
  rolls back the earlier reserves. This atomicity is the value the coordinator adds over a caller
  hand-wiring the four calls.
- **View records reuse the business modules' records.** `WarehouseState`/`DispatchResult` are
  composed of the existing public records (`Stock`, `Order`, `Worker`, `PickingTask`) — no new
  DTO shapes, no JPA types (the modules already map entities → records on the way out).
- **Constructor injection of the three ports** keeps the impl testable with real beans under
  `@SpringBootTest` and makes the unidirectional dependency explicit.

## Task list

### Phase 1 — Foundation

#### Task 1: Sealed skeleton — port + view records + impl shell
**Description:** Create the module in the sealed-port shape — public `DispatchService` (both
method signatures), public `WarehouseState` and `DispatchResult` records; a package-private
`DispatchServiceImpl` `@Service` that injects `InventoryService`, `OrderService`,
`OutboundService` via constructor (methods can throw `UnsupportedOperationException` until the
next tasks).

**Acceptance criteria:**
- [ ] Public `DispatchService`, `WarehouseState`, `DispatchResult` in `com.wms.ai.coordinator`.
- [ ] Package-private `@Service` `DispatchServiceImpl` in `com.wms.ai.coordinator.internal`, constructor-injecting the three ports.
- [ ] App context loads with the new bean (component scan picks up the package-private `@Service`).

**Verification:**
- [ ] `@SpringBootTest` context-loads test passes (or the existing context test still green with the bean present).
- [ ] `./gradlew test` green.

**Dependencies:** Modules 1–3 complete.
**Files likely touched:** `coordinator/DispatchService.java`, `coordinator/WarehouseState.java`, `coordinator/DispatchResult.java`, `coordinator/internal/DispatchServiceImpl.java`
**Estimated scope:** S

> **Checkpoint — after Task 1:** app boots with all four modules; the coordinator bean wires the
> three ports; tests green.

### Phase 2 — Read aggregation

#### Task 2: `warehouseState()` — the UI snapshot
**Description:** Compose the three ports into one read-only `WarehouseState`. If listing all
workers / all tasks is awkward through the current Outbound port, add a minimal read to
`OutboundService` (e.g. `listWorkers()`, `listTasks()`) — preferred over unioning per-status
calls — and note it in `03-outbound-module.md`.

**Acceptance criteria:**
- [ ] `warehouseState()` returns all stocks, all orders, all workers, and all tasks as the four lists of `WarehouseState`.
- [ ] No mutation; no JPA type leaks (only the public records appear).
- [ ] If `OutboundService` gained reads, they follow the module's existing read conventions and have their own module test.

**Verification:**
- [ ] `@SpringBootTest` (dev profile or repository-seeded fixtures): `warehouseState()` reflects the seeded stocks/orders/workers and an empty task list initially.

**Dependencies:** Task 1.
**Files likely touched:** `coordinator/internal/DispatchServiceImpl.java`, possibly `outbound/OutboundService.java` + `outbound/internal/OutboundServiceImpl.java`, `src/test/.../coordinator/internal/DispatchServiceStateTest.java`
**Estimated scope:** S–M

### Phase 3 — The atomic composite

#### Task 3: `assignOrderToWorker(orderId, workerId)` — transactional composite + guardrails
**Description:** Implement the four-step composite per "Confirmed decisions", `@Transactional`,
reserving each item line, then Order → ASSIGNED, Worker → BUSY, createTask. Translate a `false`
from `reserve` into `IllegalStateException`. Surface all module guardrail exceptions unchanged.

**Acceptance criteria:**
- [ ] Happy path: stock reserved for every line, order → ASSIGNED, worker → BUSY, a `PickingTask` created; returns a `DispatchResult` carrying the task.
- [ ] Unknown `orderId` → `IllegalArgumentException`; insufficient stock, non-PENDING order, or non-IDLE/raced worker → `IllegalStateException`.
- [ ] **Rollback:** after any failure, stock quantities, order status, and worker status are all **unchanged** from before the call (no partial reserve, no orphan task).

**Verification:**
- [ ] `@SpringBootTest`, fixtures seeded via the modules' repositories:
  - success path asserts all four effects;
  - **insufficient stock** (e.g. the seeded out-of-stock `SKU-2002` order) → throws, and stock/order/worker unchanged;
  - **non-PENDING order** (pre-set to ASSIGNED) → throws, unchanged;
  - **non-IDLE worker** (pre-set BUSY) → throws, and stock **not** consumed (reserve rolled back).
- [ ] `./gradlew test` green.

**Dependencies:** Task 1 (Task 2 not required but usually landed first).
**Files likely touched:** `coordinator/internal/DispatchServiceImpl.java`, `src/test/.../coordinator/internal/DispatchServiceAssignTest.java`
**Estimated scope:** M

> **Checkpoint — after Task 3:** a full assignment runs atomically; every guardrail failure rolls
> back cleanly; tests green.

### Phase 4 — Boundary guard

#### Task 4: Extend the ArchUnit guard to `coordinator.internal`
**Description:** Add a rule so nothing outside the coordinator module reaches into
`com.wms.ai.coordinator.internal`, mirroring the three existing module rules (or generalizing all
into one parameterized rule over the module packages). Confirm the intended *new* edge — the
coordinator depending on the three business **ports** (not their internals) — is allowed and that
no business module depends on the coordinator.

**Acceptance criteria:**
- [ ] `ArchitectureTest` asserts no type outside `com.wms.ai.coordinator..` depends on `com.wms.ai.coordinator.internal..`.
- [ ] (Optional but recommended) a rule asserting `com.wms.ai.{inventory,order,outbound}..` do **not** depend on `com.wms.ai.coordinator..` — the dependency stays one-directional.

**Verification:**
- [ ] `./gradlew test` — the rule(s) pass against the current code.

**Dependencies:** Task 1.
**Files likely touched:** `src/test/java/com/wms/ai/ArchitectureTest.java`
**Estimated scope:** S

> **Checkpoint — Complete:** all AC met; `./gradlew clean test` green; the module exposes only
> `DispatchService` + `WarehouseState`/`DispatchResult`; the impl is sealed; the coordinator
> depends only on the three business **ports**; no business module depends on the coordinator.

## Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Composite not actually atomic (reserve commits, later step fails) | High (lost/locked stock) | Single `@Transactional` over all four steps; rollback explicitly tested for each failure mode (Task 3) |
| `reserve` returning `false` silently ignored | High (assign "succeeds" with no stock) | Translate `false` → `IllegalStateException` to abort + roll back; covered by the out-of-stock test |
| Multi-line order partially reserved on a mid-loop failure | Med | All reserves in the same transaction; failure rolls back the whole loop |
| Coordinator reaching into a module's `internal` to read all workers/tasks | Med (breaks sealed port) | Add a public read to `OutboundService` instead (Task 2); ArchUnit guard (Task 4) |
| New "up→down" edge mistaken for a cycle | Low | Explicit ArchUnit rule that business modules don't depend on the coordinator (Task 4) |

## Verification (end to end)

- `./gradlew test` — coordinator tests green (context load, `warehouseState` snapshot, assign
  happy path + the three rollback cases) plus all existing module tests.
- `./gradlew bootRun --args='--spring.profiles.active=dev'` — context loads all four modules; the
  coordinator can produce a `warehouseState()` reflecting the seeded data and run an
  `assignOrderToWorker` against a seeded PENDING order + IDLE worker.
- Boundary: no production type outside `com.wms.ai.coordinator` references
  `com.wms.ai.coordinator.internal`; no business module references `com.wms.ai.coordinator`.
