# WMS-AI Experimental Project Design

## 1. Project Goals

Replace the human dispatcher's decision-making in a traditional WMS outbound flow with an **AI module**. The experiment aims to validate:

1. Whether the **AI Agent + Tool Calling** pattern is viable in a WMS context
2. Whether the three existing business modules (Inventory / Order / Outbound) can be orchestrated by AI **without being aware of its existence**
3. Whether the dispatch decision quality can **at least match a human dispatcher** (this is the question the evaluation phase will answer)

Non-goals (out of scope for this iteration):

- Picking path optimization (TSP)
- Multi-warehouse, cross-warehouse dispatch
- Real persistence (use in-memory for now)
- Worker-facing UI

---

## 2. Overall Architecture

```
                       ┌──────────────────┐
                       │   AI Module      │  ← the new coordinator
                       │  (LLM + Tools)   │
                       └────────┬─────────┘
                                │ unidirectional
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
      ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
      │  Inventory   │  │   Order      │  │   Outbound   │
      │   Module     │  │   Module     │  │   Module     │
      └──────────────┘  └──────────────┘  └──────────────┘
```

**Design principles:**

- **Unidirectional dependency**: Business modules have zero knowledge of AI — no AI-related imports. AI module depends on business modules, never the reverse. This way, swapping AI out for a rules engine or a human UI later stays clean.
- **Guardrails live in the business layer, not in the prompt**: Stock checks, order-status validation, worker availability — all enforced inside the business module APIs. The LLM cannot bypass them by hallucinating.
- **Traceable decisions**: Every tool call and decision outcome is logged so it can be audited after the fact.

---

## 3. Module Design

### 3.1 Inventory Module

**Purpose**: Maintain the source-of-truth state of SKU stock.

**Responsibilities**:

- Maintain quantity and storage location per SKU
- Provide queries: `getStock(sku)`, `listAll()`
- Provide reservation: `reserve(sku, quantity)` — atomic, returns success/failure
- Provide release: `release(sku, quantity)` — restock on picking failure or order cancellation

**Not responsible for**:

- Deciding _whether_ to reserve stock (that belongs to AI or business workflow)
- Knowing what an order looks like

**Exposed data**:

```
Stock { sku, quantity, location }
```

---

### 3.2 Order Module

**Purpose**: Manage the order lifecycle.

**Responsibilities**:

- Accept and persist incoming orders
- Maintain the order state machine: `PENDING → ASSIGNED → PICKING → PICKED → SHIPPED` (or `CANCELLED`)
- Provide queries: `get(id)`, `listByStatus(status)`, `listAll()`
- Provide state transitions: `updateStatus(id, newStatus)`

**Not responsible for**:

- Deciding who processes the order or when
- Mutating stock directly (must go through Inventory)
- Assigning workers (must go through Outbound)

**Exposed data**:

```
Order { id, customer, items[], priority, dueAt, status }
OrderItem { sku, quantity }
Priority: LOW | NORMAL | HIGH | URGENT
```

---

### 3.3 Outbound Module

**Purpose**: Manage the executional side of outbound — workers and picking tasks.

**Responsibilities**:

- Manage workers: `Worker { id, name, currentZone, status }` with status `IDLE | BUSY | OFFLINE`
- Manage picking tasks: `PickingTask { id, orderId, workerId, assignedAt, status }`
- Provide worker queries: `listByStatus(status)`, `get(id)`
- Provide task creation: `createTask(orderId, workerId)`
- Provide task status updates

**Not responsible for**:

- Deciding _which_ order goes to _which_ worker (that's the AI module's job)
- Stock reservation or order state changes (those belong to Inventory / Order; the coordinator wires them together)

---

### 3.4 AI Module (the new coordinator)

**Purpose**: Replace the human dispatcher. Automate the full decision chain: _read state → reason about priority → assign work_.

**Core components**:

1. **Tools (exposed to the LLM)**
   - `listPendingOrders()` — return all orders awaiting dispatch
   - `getStock(sku)` — return quantity and storage zone for a SKU
   - `listAvailableWorkers()` — return all IDLE workers
   - `assignOrderToWorker(orderId, workerId)` — **atomic assignment** that internally performs "reserve stock + update order status + update worker status + create PickingTask"

2. **System Prompt (the decision policy)**
   Tells the LLM its role, what factors to consider (priority, due time, stock, worker availability, zone affinity), and the output format.

3. **Orchestrator**
   Triggers one full dispatch cycle. Trigger source can be a REST endpoint, a Spring Application Event, or a scheduler.

**Responsibility boundaries**:

- The AI module **only makes decisions and coordinates** — it never touches data directly. All state mutations go through business module APIs.
- The AI module **can read** any business state, but **writes only happen through composite operations like `assignOrderToWorker`** — this guarantees guardrails always run.

---

## 4. A Dispatch Cycle, End to End

```
1. An order enters the system
   └─ Order Module stores it with status = PENDING

2. AI dispatch is triggered (REST trigger / event / scheduler)
   └─ DispatchOrchestrator.dispatch()

3. AI Agent autonomously runs tool calls (order chosen by the LLM):
   ├─ listPendingOrders()        → list of orders awaiting dispatch
   ├─ listAvailableWorkers()     → list of IDLE workers
   ├─ getStock(sku) × N          → verify stock for each order's items
   └─ Reasoning: rank by priority → dueAt → stock feasibility → zone affinity
      to produce an assignment plan

4. AI executes the plan by calling assignOrderToWorker(orderId, workerId)
   └─ This tool does four things atomically:
      ├─ Inventory.reserve(...)          ← reserve stock
      ├─ Order.updateStatus(ASSIGNED)    ← transition order state
      ├─ Worker.updateStatus(BUSY)       ← transition worker state
      └─ PickingTask.create(...)         ← create the task

5. Worker picks up the PickingTask from their queue
   └─ Walks to the bin and picks. No decisions required from the worker.
```

---

## 5. Decision Factors (the AI policy)

Ranked by importance:

| #   | Factor              | Notes                                                                                                  |
| --- | ------------------- | ------------------------------------------------------------------------------------------------------ |
| 1   | Priority            | URGENT > HIGH > NORMAL > LOW                                                                           |
| 2   | Due time            | Sooner deadline first                                                                                  |
| 3   | Stock availability  | Skip orders with insufficient stock; log the reason                                                    |
| 4   | Worker availability | Only IDLE workers can be assigned                                                                      |
| 5   | Zone affinity       | Prefer pairing an order with a worker already in the same zone as the items, to reduce travel distance |

This policy lives in the System Prompt. Adjusting AI behavior means editing the prompt.

---

## 6. Failure Modes and Handling

| Scenario                             | Handling                                                                                                                       |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| Insufficient stock                   | `assignOrderToWorker` throws `IllegalStateException`; the LLM receives the error, skips the order, and notes it in the summary |
| Order not in PENDING state           | Same as above                                                                                                                  |
| Worker no longer IDLE (raced)        | Same as above                                                                                                                  |
| LLM loops on tool calls indefinitely | Spring AI has a default max-iterations cap; set an explicit limit                                                              |
| LLM produces zero assignments        | Result is empty; UI/log still surfaces the reasoning trace                                                                     |

The guiding principle: **business module APIs are the source of truth. If the LLM issues a wrong instruction, let it fail. Do NOT relax the checks just to make the LLM "succeed".**

---

## 7. Future Extensions

Out of scope for this iteration, but the design leaves room for:

1. **Multi-task queues per worker** — workers can hold multiple tasks; AI must consider load balancing
2. **Picking-path optimization** — add a `computePickingRoute` tool backed by A\* or OR-Tools. This is the LLM-plus-algorithm hybrid pattern
3. **Event-driven dispatch** — new orders auto-trigger a dispatch cycle instead of needing a REST call
4. **Evaluation harness** — generate N synthetic scenarios, run the AI dispatcher, a greedy baseline, and human-labeled ground truth, then compare KPIs (SLA hit rate, total picking time, idle time)
5. **Persistence** — swap in-memory stores for JPA repositories
6. **Cross-provider comparison** — run the same prompt against Claude / GPT / Gemini and compare decision quality

---

## 8. Success Criteria for the Experiment

After the MVP runs, ask:

- [ ] Does the AI correctly skip orders with insufficient stock instead of failing?
- [ ] Is the dispatch order reasonable (URGENT first, nearer deadline first)?
- [ ] Does the AI use zone affinity to pair ZONE-1 orders with ZONE-1 workers?
- [ ] Is the reasoning trace clear enough that a senior dispatcher can debate "why this assignment" with you?
- [ ] Are latency and cost acceptable (single dispatch within seconds, single-run cost within a few cents)?

If most boxes are checked, the pattern is worth pushing further: add evaluation, add persistence, and start thinking about what production deployment would look like.
