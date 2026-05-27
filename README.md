# WMS-AI Experimental Project Design

## 1. Project Goals

Build a warehouse outbound-dispatch system where the **dispatcher role is pluggable**, and use
it to validate whether an AI dispatcher can match a human one. The dispatcher — the thing that
decides _which order goes to which worker_ — is not baked into the business modules. It sits
behind a single seam (the **coordinator**) that anything can drive. We exercise that seam in two
phases:

- **Phase A — Human dispatcher.** A person drives dispatch through a web console with a **live
  warehouse visualization**. This lets us _experience_ the module interactions and guardrails
  first-hand, and it captures a human decision **baseline**.
- **Phase B — AI dispatcher.** An **AI module** plugs into the **same** coordinator seam and is
  watched through the **same** visualization. Because both phases drive identical operations, AI
  decisions can be compared directly against the human baseline.

The experiment aims to validate:

1. Whether the **AI Agent + Tool Calling** pattern is viable in a WMS context
2. Whether the three business modules (Inventory / Order / Outbound) can be orchestrated **without
   being aware of who orchestrates them** — human or AI
3. Whether the AI's dispatch decision quality can **at least match a human dispatcher** (Phase A
   gives us the baseline to judge against)

Non-goals (out of scope for this iteration):

- Picking path optimization (TSP)
- Multi-warehouse, cross-warehouse dispatch
- A production datastore (persistence runs on Spring Data JPA backed by an in-memory H2 database — no external DB, no cross-warehouse persistence concerns this iteration)
- Worker-facing UI (the console is dispatcher-facing; workers just receive tasks)

---

## 2. Overall Architecture

```
        ┌─────────────────────────────────────────────┐
        │   Vue 3 + Vite SPA                           │
        │   dispatch console + live visualization      │
        └───────────────────────┬─────────────────────┘
                                 │ HTTP / JSON  (polls GET /api/state)
        ┌───────────────────────▼─────────────────────┐
        │   Spring REST layer  (com.wms.ai.web)        │
        └───────────────────────┬─────────────────────┘
                                 ▼
        ┌─────────────────────────────────────────────┐
        │   Coordinator module  (the pluggable seam)   │
        │   assignOrderToWorker() + warehouseState()   │
        └───────────────────────┬─────────────────────┘
              the dispatcher that drives the seam:
              ┌──────────────────┴──────────────────┐
              │  Phase A: Human (via the console)    │
              │  Phase B: AI module (LLM + Tools)    │  ← same seam, swapped in later
              └──────────────────┬──────────────────┘
                                 │ unidirectional — modules know nothing above this line
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
      ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
      │  Inventory   │  │   Order      │  │   Outbound   │
      │   Module     │  │   Module     │  │   Module     │
      └──────────────┘  └──────────────┘  └──────────────┘
```

**Design principles:**

- **Unidirectional dependency**: Business modules have zero knowledge of the coordinator, the
  web layer, the UI, or the AI — no imports pointing "up". The coordinator depends on the
  business modules' ports, never the reverse. The web layer depends on the public ports, never
  on internals.
- **Pluggable dispatcher**: The human console and the AI module are **interchangeable drivers of
  one coordinator seam**. Swapping AI in (or out, for a rules engine) changes only who calls
  `assignOrderToWorker` — the seam, the modules, and the visualization stay put.
- **Guardrails live in the business layer, not in the prompt or the caller**: Stock checks,
  order-status validation, worker availability — all enforced inside the business module APIs.
  Neither a hallucinating LLM nor a mistaken human click can bypass them.
- **Visualization-first**: Every state change is observable in the console — for the human in
  Phase A and for the AI in Phase B. The same map, board, and event log render both.
- **Traceable decisions**: Every dispatch action and its outcome (success or guardrail failure)
  is surfaced in the event log so it can be audited after the fact.
- **Feature + sealed port (module structure)**: Each module is a single package named after the
  feature and exposes exactly **one public interface — its port** (e.g. `InventoryService`) plus
  immutable view records. The JPA entity, repository, and `@Service` implementation live
  package-private in a child `internal` package, so no other module can reach them at compile
  time. An ArchUnit test guards each boundary.

---

## 3. Module Design

### 3.1 Inventory Module

**Purpose**: Maintain the source-of-truth state of SKU stock.

**Port** (`InventoryService`):

```java
Optional<Stock> getStock(String sku);
List<Stock>     listAll();
boolean         reserve(String sku, int quantity);   // atomic; returns success/failure
void            release(String sku, int quantity);    // restock on failure/cancellation
```

**Not responsible for**: deciding _whether_ to reserve (that's the dispatcher's call); knowing
what an order looks like.

**Exposed data**:

```
Stock { sku, quantity, location }
```

---

### 3.2 Order Module

**Purpose**: Manage the order lifecycle.

**Port** (`OrderService`):

```java
Order           submit(NewOrder draft);                 // assigns a UUID id, status = PENDING
Optional<Order> get(String id);
List<Order>     listByStatus(OrderStatus status);
List<Order>     listAll();
Order           updateStatus(String id, OrderStatus newStatus);   // enforces the state machine
```

State machine: `PENDING → ASSIGNED → PICKING → PICKED → SHIPPED`, with `→ CANCELLED` available
from any non-terminal state. `SHIPPED` and `CANCELLED` are terminal.

**Not responsible for**: deciding who processes the order or when; mutating stock (goes through
Inventory); assigning workers (goes through Outbound).

**Exposed data**:

```
Order     { id, customer, items[], priority, dueAt, status }
OrderItem { sku, quantity }
NewOrder  { customer, items[], priority, dueAt }   // submission input
OrderStatus : PENDING | ASSIGNED | PICKING | PICKED | SHIPPED | CANCELLED
Priority    : LOW | NORMAL | HIGH | URGENT
```

---

### 3.3 Outbound Module

**Purpose**: Manage the executional side of outbound — workers and picking tasks.

**Port** (`OutboundService`):

```java
// Workers (seeded reference data)
Optional<Worker> getWorker(String id);
List<Worker>     listWorkersByStatus(WorkerStatus status);     // IDLE = "available"
Worker           updateWorkerStatus(String workerId, WorkerStatus newStatus);

// Picking tasks (created at runtime by the dispatcher)
PickingTask      createTask(String orderId, String workerId);  // UUID id, assignedAt = now
Optional<PickingTask> getTask(String id);
PickingTask      updateTaskStatus(String taskId, TaskStatus newStatus);
```

Worker machine: `IDLE ↔ BUSY`, either `↔ OFFLINE`. Task machine:
`ASSIGNED → PICKING → DONE`, with `→ CANCELLED` from either non-terminal state.

**Not responsible for**: deciding _which_ order goes to _which_ worker (the dispatcher's job);
stock reservation or order state changes (Inventory / Order). `createTask`'s `orderId` is an
**opaque string** — Outbound has no Order dependency and never validates it.

**Exposed data**:

```
Worker      { id, name, currentZone, status }
PickingTask { id, orderId, workerId, assignedAt, status }
WorkerStatus : IDLE | BUSY | OFFLINE
TaskStatus   : ASSIGNED | PICKING | DONE | CANCELLED
```

---

### 3.4 Coordinator Module (the pluggable seam)

**Purpose**: Be the single seam any dispatcher — human or AI — drives. It is the **only** thing
that wires the three business modules together. It owns no entity of its own; it is pure
orchestration over the three public ports.

**Port** (`DispatchService`):

```java
WarehouseState warehouseState();                                  // one read snapshot for the UI
DispatchResult assignOrderToWorker(String orderId, String workerId);   // the atomic dispatch composite
void           advancePick(String taskId);                        // the atomic floor (operator) step
```

- **`warehouseState()`** aggregates the three modules into one read-only snapshot
  (`stocks`, `orders`, `workers`, `tasks`) — the single payload the visualization polls.
- **`assignOrderToWorker(orderId, workerId)`** is the **atomic dispatch composite**, run inside
  one transaction so it is all-or-nothing across modules:
  1. `Inventory.reserve(...)` every item line — if any returns `false`, throw
     `IllegalStateException` (insufficient stock), which rolls back the reserves already made
  2. `Order.updateStatus(orderId, ASSIGNED)`
  3. `Outbound.updateWorkerStatus(workerId, BUSY)`
  4. `Outbound.createTask(orderId, workerId)`

  If any step fails its guardrail (`IllegalStateException` / `IllegalArgumentException`), the
  transaction rolls back and **nothing is left half-applied**. The coordinator never relaxes a
  check to force success (see §6).

- **`advancePick(taskId)`** is the **atomic floor step** — the operator's twin to the dispatch
  composite. Keyed on the order's current status it couples the order/task/worker forward one
  milestone in one transaction (`ASSIGNED → task PICKING + order PICKING`; `PICKING → PICKED`;
  `PICKED → SHIPPED + task DONE + worker IDLE`), each sub-transition guarded by the sub-entity's
  status. It exists because there is no worker UI (§1): something has to *execute* the pick the
  planner assigned. The dev-only **floor simulator** (§3.6) drives it on a fixed cadence.

**Responsibility boundary**: the coordinator decides nothing about _which_ assignment to make —
that judgement is the dispatcher's (human in Phase A, LLM in Phase B). It only **executes** an
assignment or a floor step atomically and **reads** aggregated state. The decision (assign) and
the mechanical execution (the pick) are deliberately separate seams.

---

### 3.5 Web / REST Layer

A thin Spring MVC layer (`com.wms.ai.web`) over the public ports + the coordinator port. It
touches no module internals (ArchUnit-guarded). Endpoints:

| Method & path | Backs |
| --- | --- |
| `GET  /api/state` | `DispatchService.warehouseState()` — the snapshot the SPA polls |
| `POST /api/dispatch/assign` `{orderId, workerId}` | `DispatchService.assignOrderToWorker(...)` |
| `POST /api/orders` `(NewOrder)` | `OrderService.submit(...)` — inject a new order |
| `POST /api/orders/{id}/status` `{status}` | `OrderService.updateStatus(...)` |
| `POST /api/tasks/{id}/status` `{status}` | `OutboundService.updateTaskStatus(...)` |
| `POST /api/workers/{id}/status` `{status}` | `OutboundService.updateWorkerStatus(...)` |
| `POST /api/inventory/release` `{sku, quantity}` | `InventoryService.release(...)` (optional) |

A `@RestControllerAdvice` maps the module error contract to HTTP: `IllegalArgumentException` →
**400**, `IllegalStateException` → **409**, each with a JSON `{error, message}` body — so a
guardrail rejection is something the UI can show, not an opaque 500.

---

### 3.6 Frontend Console + Visualization

A **Vue 3 + Vite SPA** in a top-level `frontend/` directory, talking to the REST layer. In dev
it runs on the Vite dev server with a proxy (`/api → :8080`), so there is no CORS concern; for a
single-origin run it can be built (`vite build`) and served as static resources by Spring. It
polls `GET /api/state` (~every 1.5s) into a reactive store and renders:

- a **warehouse map** by zone (each worker as a card colored by `IDLE`/`BUSY`/`OFFLINE`, stock
  per SKU in its zone),
- an **order board** (kanban by `OrderStatus`; cards show customer, priority color, a `dueAt`
  countdown, and items),
- a **picking-task list** linking order ↔ worker with task status,
- a **dispatch panel** — the planner's **one decision**: pick a PENDING order + an IDLE worker →
  Assign (plus inject a new order). It does **not** advance the lifecycle: progressing the pick
  is the operator's job, not the planner's.
- an **event log** recording each action and its outcome — **including guardrail failures**
  (the 409s) verbatim.

This console _is_ the human dispatcher in Phase A. In Phase B it is unchanged; the AI's reasoning
trace simply streams into the same event log.

**The floor simulator.** There is no worker-facing UI (§1), so once a pick is assigned, a
dev-only **floor simulator** stands in for the operator and executes it: on a fixed cadence it
calls the coordinator's `advancePick` (§3.4) for every in-flight pick, walking it
`PICKING → PICKED → SHIPPED` (task → `DONE`, worker → `IDLE`). The console just visualizes the
result on the next poll — the planner assigns and watches. It is opt-in
(`wms.floor.simulator.enabled=true`, off by default so tests are unaffected) and runs
server-side, so a headless Phase B (AI-triggered) dispatch progresses to completion with no
browser open.

---

### 3.7 AI Module (the future driver)

**Purpose**: In Phase B, replace the human dispatcher — automate the decision chain _read state →
reason about priority → assign work_. It is a **driver on top of the coordinator**, not a
re-implementation of it.

**Core components**:

1. **Tools (exposed to the LLM)** — thin wrappers over the existing ports/coordinator:
   - `listPendingOrders()` → `OrderService.listByStatus(PENDING)`
   - `getStock(sku)` → `InventoryService.getStock(sku)`
   - `listAvailableWorkers()` → `OutboundService.listWorkersByStatus(IDLE)`
   - `assignOrderToWorker(orderId, workerId)` → **delegates to** `DispatchService.assignOrderToWorker`
     (the atomic composite already lives in the coordinator — the AI does not re-do it)
2. **System Prompt (the decision policy)** — role, the §5 factors, output format.
3. **Orchestrator** — triggers one dispatch cycle (REST endpoint, Spring event, or scheduler).

**Responsibility boundaries**: the AI **only decides and coordinates** — it never touches data
directly. Reads go through tools; writes go only through `assignOrderToWorker`, so guardrails
always run.

---

## 4. The Dispatch Cycle, End to End

The picture is the same in both phases — only the dispatcher differs.

### 4.1 Phase A — Human dispatch loop

```
1. An order enters the system
   └─ seeded at startup, or POST /api/orders → status = PENDING

2. The order appears on the console board (PENDING), via GET /api/state polling

3. The human reads the live state — map, board, stock — and decides:
   rank by priority → dueAt → stock feasibility → idle workers → zone affinity

4. The human clicks Assign (order + worker) → POST /api/dispatch/assign
   └─ DispatchService.assignOrderToWorker runs the atomic composite:
      ├─ Inventory.reserve(...)          ← reserve stock
      ├─ Order.updateStatus(ASSIGNED)    ← transition order state
      ├─ Outbound.updateWorkerStatus(BUSY)
      └─ Outbound.createTask(...)        ← create the picking task
   └─ on a guardrail failure the call 409s and the event log shows why; nothing changes

5. The map/board update on the next poll. The human's job ends at the assignment — the
   operator executes the pick. With no worker UI, the dev floor simulator stands in: it
   calls DispatchService.advancePick on a fixed cadence, walking the lifecycle automatically
   (ASSIGNED → PICKING → PICKED → SHIPPED; task → DONE; worker → IDLE). The console just
   watches it happen.
```

### 4.2 Phase B — AI dispatch loop

```
1–2. Identical: orders enter and surface as PENDING.

3. AI dispatch is triggered (REST / event / scheduler) → the Agent runs tool calls
   (listPendingOrders, listAvailableWorkers, getStock × N) and reasons out a plan.

4. The AI executes the plan via assignOrderToWorker(orderId, workerId), which delegates
   to the SAME DispatchService.assignOrderToWorker composite the human used in Phase A.

5. The console renders the result identically, and the same floor simulator advances each
   assigned pick to completion — so a headless, AI-triggered run progresses with no browser
   open. The AI's reasoning trace streams into the event log next to (or in place of) the
   human's actions.
```

---

## 5. Decision Factors (the dispatch policy)

Ranked by importance. In Phase A this is the rubric a human follows; in Phase B it is the policy
encoded in the System Prompt.

| #   | Factor              | Notes                                                                                                  |
| --- | ------------------- | ------------------------------------------------------------------------------------------------------ |
| 1   | Priority            | URGENT > HIGH > NORMAL > LOW                                                                           |
| 2   | Due time            | Sooner deadline first                                                                                  |
| 3   | Stock availability  | Skip orders with insufficient stock; log the reason                                                    |
| 4   | Worker availability | Only IDLE workers can be assigned                                                                      |
| 5   | Zone affinity       | Prefer pairing an order with a worker already in the same zone as the items, to reduce travel distance |

Adjusting AI behavior in Phase B means editing the prompt; the factors themselves stay constant
across phases so the comparison is fair.

---

## 6. Failure Modes and Handling

| Scenario | Handling |
| --- | --- |
| Insufficient stock | `assignOrderToWorker` throws `IllegalStateException`; the transaction rolls back. The REST layer returns **409**; the console event log shows it, and the dispatcher (human or LLM) skips the order. |
| Order not in PENDING state | Same — `Order.updateStatus` rejects the illegal transition → 409. |
| Worker no longer IDLE (raced) | Same — `Outbound.updateWorkerStatus(BUSY)` on a non-IDLE worker → 409. |
| LLM loops on tool calls indefinitely | Spring AI has a default max-iterations cap; set an explicit limit (Phase B). |
| Dispatcher produces zero assignments | Result is empty; the console still surfaces the state and (in Phase B) the reasoning trace. |

The guiding principle: **business module APIs are the source of truth. If the dispatcher issues a
wrong instruction, let it fail. Do NOT relax the checks just to make the caller "succeed".** This
holds identically for a mistaken human click and a hallucinating LLM.

---

## 7. The Visualization

One console, two phases. It renders a single `GET /api/state` snapshot and is the place where the
experiment is _watched_:

- **Warehouse map** — zones (ZONE-1/2/3); each worker a card colored by status (IDLE / BUSY /
  OFFLINE); stock per SKU shown in its zone. Makes zone affinity and worker load visible at a
  glance.
- **Order board** — kanban columns by `OrderStatus`; cards carry customer, a priority color, a
  `dueAt` countdown, and the item lines. The flow PENDING → … → SHIPPED is the story of a
  dispatch.
- **Picking-task list** — each task links an order to a worker with its `TaskStatus`.
- **Dispatch panel** — the planner's controls: **assign** a PENDING order to an IDLE worker, and
  submit a new order. Advancing the pick is the floor simulator's job, not the panel's.
- **Event log** — a chronological trace of actions and outcomes, **including guardrail 409s**. In
  Phase B the AI's reasoning trace renders here, making "why this assignment" debatable.

Live updates are polling-based this iteration (simple, good enough at this scale); Server-Sent
Events are an easy upgrade if the event log needs to stream AI tokens.

---

## 8. Future Extensions

Out of scope for this iteration, but the design leaves room for:

1. **Multi-task queues per worker** — workers hold multiple tasks; the dispatcher considers load balancing
2. **Picking-path optimization** — add a `computePickingRoute` tool backed by A\* or OR-Tools (the LLM-plus-algorithm hybrid)
3. **Event-driven dispatch** — new orders auto-trigger a dispatch cycle instead of a manual call
4. **Evaluation harness** — generate N synthetic scenarios; run the AI dispatcher, a greedy baseline, and the **human baseline captured in Phase A**, then compare KPIs (SLA hit rate, total picking time, idle time)
5. **Persistence** — swap in-memory H2 for a production datastore (e.g. Postgres); the JPA repositories stay the same
6. **Cross-provider comparison** — run the same prompt against Claude / GPT / Gemini and compare decision quality
7. **Live streaming** — replace polling with SSE/WebSocket for token-by-token AI reasoning in the event log

---

## 9. Success Criteria for the Experiment

After Phase A (human console) runs, ask:

- [ ] Can a person dispatch a full order lifecycle through the console, and does the visualization make warehouse state legible?
- [ ] Do guardrail failures (out-of-stock, raced worker, illegal transition) surface clearly in the event log without corrupting state?

After Phase B (AI plugged in) runs, ask:

- [ ] Does the AI correctly skip orders with insufficient stock instead of failing?
- [ ] Is the dispatch order reasonable (URGENT first, nearer deadline first)?
- [ ] Does the AI use zone affinity to pair ZONE-1 orders with ZONE-1 workers?
- [ ] Is the reasoning trace clear enough that a senior dispatcher can debate "why this assignment" with you?
- [ ] **Does the AI's dispatch quality at least match the human baseline captured in Phase A?**
- [ ] Are latency and cost acceptable (single dispatch within seconds, single-run cost within a few cents)?

If most boxes are checked, the pattern is worth pushing further: add the evaluation harness, add
persistence, and start thinking about what production deployment would look like.
