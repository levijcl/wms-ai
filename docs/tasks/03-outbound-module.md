# Implementation Plan: Outbound Module (Module 3)

> Third and last of the three business modules in WMS-AI. See `README.md` §3.3 for the design.
> Inventory (`01-inventory-module.md`) and Order (`02-order-module.md`) are complete and
> established the **feature + sealed port** pattern this module follows. The AI coordinator
> (README §3.4) is built *after* this, on top of all three business modules. The checkbox
> task list below doubles as the todo list.

## Context

The Inventory and Order modules established the project-wide **feature + sealed port**
pattern: one public port interface + immutable view records (+ enums) in the feature
package, with the JPA entity, repository, `@Service` impl, and seed data package-private
under `internal/`; tests use `@DataJpaTest` + `@Import(Impl.class)`; an ArchUnit rule guards
each boundary.

The Outbound module manages the **executional side** of outbound — the **workers** and the
**picking tasks** assigned to them (README §3.3). It is the third sibling leaf: per README it
must **not** decide *which* order goes to *which* worker (that is the AI coordinator's job),
and must **not** reserve stock or change order state (those belong to Inventory / Order). It
therefore has **zero dependency on the Inventory and Order modules** — the future AI
coordinator wires all three together via its composite `assignOrderToWorker` tool.

Three things are new versus Inventory and Order and shape the plan:

1. **Two aggregates behind one port.** Unlike Inventory (`Stock`) and Order (`Order`), this
   module owns two entities — `Worker` and `PickingTask` — exposed through a single
   `OutboundService` port (README §2: one port per module). Method names disambiguate the
   aggregate (`getWorker` / `getTask`).
2. **Two small state machines.** A **worker** status machine (`IDLE ↔ BUSY`, either ↔
   `OFFLINE`) and a **task** status machine (`ASSIGNED → PICKING → DONE`, or `CANCELLED`).
   The worker machine's `IDLE → BUSY` edge *is* the README §6 guardrail "only IDLE workers
   can be assigned" + the raced-worker guard.
3. **A runtime-created aggregate with an opaque foreign key.** `PickingTask` is created at
   dispatch time and carries an `orderId` *string* from the Order module — but Outbound has
   no Order dependency, so it treats `orderId` as **opaque** and never validates the order
   exists. (It does validate the referenced `workerId` exists, since workers are its own.)

## Confirmed decisions

- **One port, `OutboundService`, covers both aggregates.** Workers and picking tasks are the
  two halves of "outbound execution" and ship as one module with one port. Method names
  carry the aggregate (`getWorker`, `listWorkersByStatus`, `updateWorkerStatus`; `createTask`,
  `getTask`, `updateTaskStatus`). No REST controller this iteration (consistent with the
  other two modules).
- **Workers are seeded reference data, not created at runtime.** Like `Stock`, workers have
  **stable natural-key ids** (e.g. `WK-1`) and arrive via dev seed data — there is **no
  `createWorker`** on the port this iteration. **Picking tasks are created at runtime** via
  `createTask`, which generates a **UUID string id** and stamps `assignedAt = Instant.now()`
  (mirrors `Order` id generation).
- **No dependency on Order or Inventory.** `createTask(orderId, workerId)` takes an opaque
  `orderId` string; Outbound cannot and must not check that the order exists. It *does*
  validate `workerId` references an existing worker (workers are its own aggregate).
- **The composite is not here.** "reserve stock + update order status + update worker status
  + create task" is the future AI coordinator's `assignOrderToWorker` (README §4). Outbound
  exposes `updateWorkerStatus` and `createTask` as **independent** operations the coordinator
  wires together; it does not orchestrate them itself.
- **Worker status transitions** — the "only IDLE workers assignable" + raced-worker guard:
  | From | Allowed → |
  |------|-----------|
  | `IDLE` | `BUSY`, `OFFLINE` |
  | `BUSY` | `IDLE`, `OFFLINE` |
  | `OFFLINE` | `IDLE` |
  No self-transitions. Assigning to an already-`BUSY` worker means a requested `BUSY → BUSY`,
  which is illegal → `IllegalStateException` (this is the README §6 raced-worker case).
  Unknown id → `IllegalArgumentException`.
- **Picking-task status transitions:**
  | From | Allowed → |
  |------|-----------|
  | `ASSIGNED` | `PICKING`, `CANCELLED` |
  | `PICKING` | `DONE`, `CANCELLED` |
  | `DONE` | — (terminal) |
  | `CANCELLED` | — (terminal) |
  Illegal transition → `IllegalStateException`; unknown id → `IllegalArgumentException`.
- **Completing or cancelling a task does NOT auto-free the worker.** Worker status is updated
  independently; coupling task completion to `worker → IDLE` is the coordinator/worker-flow's
  concern (worker-facing flow is out of scope this iteration). Keeping the two aggregates
  decoupled mirrors how the coordinator wires independent module operations.

## Module layout (mirrors Inventory / Order)

```
com.wms.ai.outbound
  OutboundService           public interface — the only entry point (the "port")
  Worker                    public record (id, name, currentZone, status) — the view
  WorkerStatus              public enum IDLE | BUSY | OFFLINE
  PickingTask               public record (id, orderId, workerId, assignedAt, status) — the view
  TaskStatus                public enum ASSIGNED | PICKING | DONE | CANCELLED
  internal/
    WorkerEntity            package-private @Entity (flat; status as @Enumerated STRING)
    WorkerRepository        package-private JpaRepository<WorkerEntity, String> + findByStatus
    PickingTaskEntity       package-private @Entity (flat; assignedAt Instant; status @Enumerated STRING)
    PickingTaskRepository   package-private JpaRepository<PickingTaskEntity, String>
    OutboundServiceImpl     package-private @Service implementing the port (both aggregates)
    OutboundSeedData        package-private @Profile("dev") CommandLineRunner (seeds workers)
```

Port surface:

```java
public interface OutboundService {

    // --- Workers (seeded reference data; not created at runtime this iteration) ---
    Optional<Worker> getWorker(String id);                       // empty for unknown id
    List<Worker> listWorkersByStatus(WorkerStatus status);       // backs the AI's listAvailableWorkers() = IDLE
    List<Worker> listWorkers();                                  // whole pool — backs coordinator warehouseState()
    Worker updateWorkerStatus(String workerId, WorkerStatus newStatus);  // validates the transition

    // --- Picking tasks (created at runtime by the coordinator) ---
    PickingTask createTask(String orderId, String workerId);     // UUID id, assignedAt = now, status = ASSIGNED
    Optional<PickingTask> getTask(String id);                    // empty for unknown id
    List<PickingTask> listTasks();                               // all tasks — backs coordinator warehouseState()
    PickingTask updateTaskStatus(String taskId, TaskStatus newStatus);   // validates the transition
}
```

> **Added in coordinator Task 2 (`04-coordinator-module.md`):** `listWorkers()` and
> `listTasks()` are whole-pool reads (no per-status filter) added so the coordinator's
> `warehouseState()` can aggregate all workers and tasks without unioning per-status calls or
> reaching into `internal`. They follow the module's read conventions (flat entities, no
> transaction needed) and are covered by `OutboundServiceListAllTest`.

## Architecture decisions

- **Sealed port.** Public types are the port + the two view records + the two enums. Both
  `@Entity` types (`WorkerEntity`, `PickingTaskEntity`) stay package-private in `internal/`;
  the impl maps `WorkerEntity → Worker` and `PickingTaskEntity → PickingTask` so JPA types
  never cross the boundary — same discipline as `StockEntity → Stock` and `OrderEntity → Order`.
- **Both entities are flat** — no `@ElementCollection`, so reads have **none** of the Order
  module's lazy-collection pitfall. Reads can be plain (non-`@Transactional`) like Inventory's
  `getStock`/`listAll`; the three **mutations** (`updateWorkerStatus`, `createTask`,
  `updateTaskStatus`) are `@Transactional`.
- **Enums persisted as `@Enumerated(EnumType.STRING)`** — stable across reordering, readable
  in the H2 console.
- **Both state machines live in the service layer** (two transition tables in
  `OutboundServiceImpl`), not on the public enums — guardrails belong in the business layer
  per README §2, and keeping policy off the enums keeps the public surface clean. This is the
  exact pattern `OrderServiceImpl.ALLOWED_TRANSITIONS` already uses.
- **Two tables, named explicitly** (`worker`, `picking_task`) for readability; neither is a
  reserved SQL word (unlike Order's `orders`), so this is cosmetic.
- **No dependency on Order or Inventory.** Outbound neither imports nor calls them;
  `orderId` is an opaque string. The three modules are independent leaves wired only by the
  future AI coordinator (enforced by the ArchUnit rule in Task 7 and by review).

## Task list

### Phase 1 — Foundation

#### Task 1: Sealed skeleton — port + view/enum types + both entities + both repositories
**Description:** Create the module in the sealed-port shape — public port (all method
signatures), the two view records, the two enums; package-private entities and repositories
for both aggregates.

**Acceptance criteria:**
- [ ] Public `OutboundService`, `Worker`, `WorkerStatus`, `PickingTask`, `TaskStatus` in `com.wms.ai.outbound`.
- [ ] Package-private `WorkerEntity` (`@Id String id`; `name`; `currentZone`; `status` as `@Enumerated(STRING)`) and `WorkerRepository extends JpaRepository<WorkerEntity, String>` with `List<WorkerEntity> findByStatus(WorkerStatus)`, in `com.wms.ai.outbound.internal`.
- [ ] Package-private `PickingTaskEntity` (`@Id String id`; `orderId`; `workerId`; `assignedAt` as `Instant`; `status` as `@Enumerated(STRING)`) and `PickingTaskRepository extends JpaRepository<PickingTaskEntity, String>`, in `com.wms.ai.outbound.internal`.

**Verification:**
- [ ] `@DataJpaTest`: save a `WorkerEntity` → `findById` round-trips all fields; save a `PickingTaskEntity` → `findById` round-trips all fields (incl. the `assignedAt` `Instant`).
- [ ] `./gradlew test` green.

**Dependencies:** None
**Files likely touched:** `outbound/OutboundService.java`, `outbound/Worker.java`, `outbound/WorkerStatus.java`, `outbound/PickingTask.java`, `outbound/TaskStatus.java`, `outbound/internal/WorkerEntity.java`, `outbound/internal/WorkerRepository.java`, `outbound/internal/PickingTaskEntity.java`, `outbound/internal/PickingTaskRepository.java`, `src/test/.../outbound/internal/WorkerRepositoryTest.java`, `src/test/.../outbound/internal/PickingTaskRepositoryTest.java`
**Estimated scope:** M

> **Checkpoint — after Task 1:** app boots with all three modules; a worker and a picking
> task each persist and reload; tests green.

### Phase 2 — Worker API

#### Task 2: Worker reads — `getWorker(id)`, `listWorkersByStatus(status)`
**Description:** Create the `@Service` `OutboundServiceImpl` and implement the worker read
side, mapping `WorkerEntity → Worker`.

**Acceptance criteria:**
- [ ] `@Service` `OutboundServiceImpl` in `internal`; `getWorker` → `Optional<Worker>` (empty for unknown id); `listWorkersByStatus` filters via `WorkerRepository.findByStatus`.
- [ ] `WorkerEntity → Worker` mapping done in the impl; no entity escapes the port.

**Verification:**
- [ ] Test with workers of mixed statuses: known id present / unknown empty; `listWorkersByStatus(IDLE)` returns only the IDLE workers.

**Dependencies:** Task 1
**Files likely touched:** `outbound/internal/OutboundServiceImpl.java`, `src/test/.../outbound/internal/OutboundServiceWorkerReadTest.java`
**Estimated scope:** S

#### Task 3: `updateWorkerStatus(workerId, newStatus)` — worker status guardrail
**Description:** Implement the worker transition table and enforce it. The `IDLE → BUSY`
edge is the README §6 "only IDLE workers can be assigned" + raced-worker guard the AI
coordinator's `assignOrderToWorker` relies on.

**Acceptance criteria:**
- [ ] Transition table per "Confirmed decisions"; a legal transition persists the new status and returns the updated `Worker`.
- [ ] Illegal transition (incl. self-transition, and `BUSY → BUSY` for an already-busy worker) → `IllegalStateException`; unknown id → `IllegalArgumentException`.

**Verification:**
- [ ] Parameterized tests covering each legal transition (succeeds) and a representative set of illegal ones (throw), explicitly including the raced-worker case (assigning a worker that is already `BUSY`); unknown id throws.

**Dependencies:** Task 1, Task 2
**Files likely touched:** `outbound/internal/OutboundServiceImpl.java`, `src/test/.../outbound/internal/OutboundServiceWorkerStatusTransitionTest.java`
**Estimated scope:** S–M

> **Checkpoint — after Task 3:** the full worker API works; non-IDLE workers cannot be
> (re-)assigned; tests green.

### Phase 3 — Picking-task API

#### Task 4: `createTask(orderId, workerId)` + `getTask(id)`
**Description:** Implement task creation — generate a UUID id, stamp `assignedAt = now`,
default status to `ASSIGNED`, persist, and map back to the `PickingTask` view. Add the
single-task read.

**Acceptance criteria:**
- [ ] `createTask` returns a `PickingTask` with a non-null generated id, `status == ASSIGNED`, `assignedAt` set, and the `orderId`/`workerId` echoed; the task is persisted.
- [ ] Validation → `IllegalArgumentException`: blank `orderId`, blank `workerId`, or a `workerId` that is not an existing worker. **Does not** validate the order exists (Outbound has no Order dependency — `orderId` is opaque).
- [ ] `getTask` → `Optional<PickingTask>` (empty for unknown id).

**Verification:**
- [ ] Test: `createTask` then `getTask(id)` returns an equal task; blank `orderId`/`workerId` throw; unknown `workerId` throws; an arbitrary (unknown-to-Outbound) `orderId` is accepted.

**Dependencies:** Task 1, Task 2 (worker-existence check reuses the worker read/repository)
**Files likely touched:** `outbound/internal/OutboundServiceImpl.java`, `src/test/.../outbound/internal/OutboundServiceCreateTaskTest.java`
**Estimated scope:** S

#### Task 5: `updateTaskStatus(taskId, newStatus)` — task status guardrail
**Description:** Implement the task transition table and enforce it.

**Acceptance criteria:**
- [ ] Transition table per "Confirmed decisions"; a legal transition persists the new status and returns the updated `PickingTask`.
- [ ] Illegal transition (incl. any move out of terminal `DONE`/`CANCELLED`, and self-transitions) → `IllegalStateException`; unknown id → `IllegalArgumentException`.

**Verification:**
- [ ] Parameterized tests covering each legal transition (succeeds) and a representative set of illegal ones + terminal-state moves (throw); unknown id throws.

**Dependencies:** Task 1, Task 4
**Files likely touched:** `outbound/internal/OutboundServiceImpl.java`, `src/test/.../outbound/internal/OutboundServiceTaskStatusTransitionTest.java`
**Estimated scope:** S–M

> **Checkpoint — after Task 5:** the full Outbound API works; illegal worker and task
> transitions are rejected; tests green.

### Phase 4 — Seed & polish

#### Task 6: Dev-profile seed data (workers)
**Description:** Seed a sample labour pool so dispatch experiments have workers to assign —
several `IDLE` workers, **multiple in `ZONE-1`** (so the README §8 zone-affinity criterion has
a real choice), and at least one non-`IDLE` (`BUSY`/`OFFLINE`) worker to exercise the IDLE
filter. The `picking_task` table starts **empty** — tasks are created at runtime by dispatch.

**Acceptance criteria:**
- [ ] Package-private `OutboundSeedData` `@Profile("dev")` `CommandLineRunner`, idempotent (`count() > 0` guard), seeding several workers across zones/statuses with stable ids (e.g. `WK-1`…).
- [ ] Does not run under the default test profile; does not duplicate on restart.

**Verification:**
- [ ] Test mirroring `SeedDataTest` (in the `internal` package, using the repository directly), or `./gradlew bootRun --args='--spring.profiles.active=dev'` → `listWorkersByStatus(IDLE)` returns the seeded idle workers.

**Dependencies:** Task 1, Task 2
**Files likely touched:** `outbound/internal/OutboundSeedData.java`, `src/test/.../outbound/internal/OutboundSeedDataTest.java`
**Estimated scope:** S

#### Task 7: Extend the ArchUnit boundary guard to `outbound.internal`
**Description:** Add a rule so nothing outside the Outbound module reaches into
`com.wms.ai.outbound.internal`, locking the sealed-port pattern for Module 3 as it is for
Inventory and Order.

**Acceptance criteria:**
- [ ] `ArchitectureTest` asserts no type outside `com.wms.ai.outbound..` depends on `com.wms.ai.outbound.internal..` (mirror the two existing rules, or generalize all three into one parameterized rule over the module packages).

**Verification:**
- [ ] `./gradlew test` — the rule passes against the current code.

**Dependencies:** Task 1
**Files likely touched:** `src/test/java/com/wms/ai/ArchitectureTest.java`
**Estimated scope:** S

> **Checkpoint — Complete:** all AC met; `./gradlew test` green; module exposes only
> `OutboundService` + `Worker`/`PickingTask` + `WorkerStatus`/`TaskStatus`; no AI imports;
> no dependency on Inventory or Order; internals don't leak.

## Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Two aggregates in one impl bloat or entangle `OutboundServiceImpl` | Med | Keep methods small and grouped by aggregate; two clearly-labelled transition-table constants; per-aggregate test classes |
| Worker race guard wrong (a `BUSY` worker still assignable) | High (double-booked worker) | `IDLE → BUSY` enforced by the transition table; parameterized tests include the already-`BUSY` raced case (Task 3) |
| Task state-machine drift / missing or wrong transition | High (bad task state) | Explicit transition table + parameterized tests over all legal and representative illegal moves (Task 5) |
| Accidental coupling to Order/Inventory (e.g. validating `orderId`) | Med (breaks unidirectional design) | `orderId` treated as opaque; no Order/Inventory import or call; ArchUnit (Task 7) + review |
| Enum persisted as ordinal → brittle on reorder | Low | `@Enumerated(EnumType.STRING)` |

## Verification (end to end)

- `./gradlew test` — all module tests green (two repository round-trips, worker reads, worker
  status transitions, create-task, task status transitions, seed, ArchUnit).
- `./gradlew bootRun --args='--spring.profiles.active=dev'` — context loads all three modules;
  the worker seed runs; H2 console at `/h2-console` shows the `WORKER` table populated and an
  (initially empty) `PICKING_TASK` table.
- Boundary: confirm no production type outside `com.wms.ai.outbound` references
  `com.wms.ai.outbound.internal` (ArchUnit), and the module has no `com.wms.ai.order` /
  `com.wms.ai.inventory` / AI references.
