# Implementation Plan: AI Module — Phase B Dispatcher (Module 7)

> The LLM dispatcher that replaces the human in **Phase B**. See `README.md` §3.7, §4.2, §5.
> It is a **driver on top of the coordinator**, not a re-implementation of it: it *reads* through
> tools and *writes* only through `DispatchService.assignOrderToWorker`, so every guardrail still
> runs. It plugs into the **same** seam the human console drives in Phase A and is watched through
> the **same** visualization (the reasoning trace streams into the existing event log). The
> checkbox task list below doubles as the todo list.

## Context

Everything beneath the dispatcher is in place and validated (Phase A, README §9): the three
business modules, the coordinator's atomic `assignOrderToWorker` + `advancePick`, the REST layer,
the Vue console, and the floor simulator that executes assigned picks server-side. Phase B swaps
*who decides the assignment* from a human click to an LLM agent — nothing below the seam changes.

The toolchain is already provisioned: `build.gradle` pins **Spring AI `2.0.0-M6`** (the milestone
targeting Spring Boot 4) and the **`spring-ai-starter-model-anthropic`** starter, so the agent is
built on Spring AI's `ChatClient` + `@Tool` calling against **Claude**. The Anthropic
autoconfiguration tolerates a missing API key at startup (today's tests prove it — the starter is
already on the classpath and the suite is green), so adding the agent will not break boot or CI;
only an *actual* dispatch call needs a key.

Two things shape the plan:

1. **The deterministic parts are TDD'd without the LLM.** The tools are thin wrappers over the
   existing ports/coordinator — they are unit-tested by calling them directly. Only the agent's
   model call is non-deterministic; that is exercised with a mocked `ChatModel` for logic and a
   **key-gated** integration test for a real round-trip (skipped in CI).
2. **A failing guardrail must not abort the agent.** The `assignOrderToWorker` tool catches the
   module exception and returns it to the model as a readable tool result, so the LLM *skips* the
   order (README §3.7) — the guardrail still ran and was never relaxed (README §6).

## Confirmed decisions

- **Package `com.wms.ai.agent`** — a flat driver package (like `com.wms.ai.floor`, no `internal/`
  split: it owns no entity and exposes no port). It depends only on the **public** ports
  (`DispatchService`, `OrderService`, `InventoryService`, `OutboundService`) + view records —
  never an `internal/` package. `ArchitectureTest` gains a rule that no business module depends on
  `com.wms.ai.agent`, keeping the dependency one-directional (mirrors the coordinator rule).
- **Spring AI + Claude.** Build the agent with `ChatClient`, register tools with `@Tool`, set an
  explicit **max tool-iteration cap** (README §6). Config under `spring.ai.anthropic`:
  `api-key: ${ANTHROPIC_API_KEY:}` (empty default so boot never fails), a configurable model
  (**default `claude-haiku-4-5`** — cheapest, fits the §9 latency/cost target; `claude-sonnet-4-6`
  / `claude-opus-4-7` available for higher quality), `temperature: 0` for reproducibility.
- **Tools (read + the one write)** — thin wrappers, README §3.7:
  | Tool | Delegates to |
  |---|---|
  | `listPendingOrders()` | `OrderService.listByStatus(PENDING)` |
  | `getStock(sku)` | `InventoryService.getStock(sku)` |
  | `listAvailableWorkers()` | `OutboundService.listWorkersByStatus(IDLE)` |
  | `assignOrderToWorker(orderId, workerId)` | `DispatchService.assignOrderToWorker(...)` — catches the guardrail exception and returns it as a tool result so the model can skip |
- **Trigger = REST endpoint; one cycle = one assignment.** `POST /api/dispatch/ai` runs **one**
  dispatch cycle on demand, in which the agent assigns the **single best** pending order (at most
  one assignment) — the exact Phase B analog of one human Assign click; click again to dispatch
  the next. (A scheduler/auto-trigger is a README §8 future extension, out of scope.)
- **Result shape** returned to the browser: `AiDispatchResult(List<AssignmentOutcome> outcomes,
  String reasoning)`, where `AssignmentOutcome(orderId, workerId, assigned, detail)` records each
  attempted assignment (success) or skip (with the guardrail/why detail). The console renders
  these into the **existing event log** as a new `ai` entry kind — no new persistence (the trace
  is client-side, exactly like Phase A actions).
- **No new auth, no persistence of the trace, dev-focused** — consistent with the rest of the
  experiment. The system prompt is a `classpath:` resource so tuning it needs no recompile.

## Module layout

```
com.wms.ai.agent
  DispatchTools        @Tool methods: listPendingOrders / getStock / listAvailableWorkers /
                       assignOrderToWorker (the only write; catches + reports guardrail failures)
  DispatchAgent        builds the ChatClient (system prompt + tools + max-iteration cap); runs one
                       cycle; returns AiDispatchResult
  AssignmentOutcome    record (String orderId, String workerId, boolean assigned, String detail)
  AiDispatchResult     record (List<AssignmentOutcome> outcomes, String reasoning)
  resources/prompts/dispatcher-system.st   the §5 decision policy (role, ranked factors, output)

com.wms.ai.web
  AiDispatchController  POST /api/dispatch/ai → DispatchAgent.dispatchOnce() → AiDispatchResult

frontend/src
  api/client.js         + aiDispatch()  → POST /api/dispatch/ai
  stores/useWarehouse.js + runAiDispatch(): POST, append the trace as `ai` events, then refresh
  components/DispatchPanel.vue  + a "Run AI dispatch" button (emits ai-dispatch)
  components/EventLog.vue       + styling for the `ai` entry kind
  App.vue                       wires @ai-dispatch → store.runAiDispatch()
```

## Architecture decisions

- **The AI only decides and coordinates; it never touches data directly.** Reads go through the
  read tools; the sole write tool delegates to the coordinator composite, so guardrails always run
  (README §3.7). The agent re-uses `assignOrderToWorker` — it does **not** re-do the reserve +
  status + task orchestration.
- **Guardrail failures are tool results, not aborts.** The write tool returns
  `"SKIPPED <orderId>: insufficient stock for SKU-2002"` (verbatim module message) so the model
  reasons about it and moves on. This is the §6 principle expressed for tool-calling: let it fail,
  report it, never relax the check.
- **Determinism boundary.** Tools and the controller are fully deterministic and unit-tested with
  the real beans / MockMvc; only `DispatchAgent`'s model call is not, and it is isolated behind one
  method so it can be mocked (logic tests) or key-gated (a real round-trip).
- **Same seam, same visualization.** The endpoint and the event-log rendering are all that is new
  on the surface; the floor simulator then executes the AI's assignments exactly as it does the
  human's, so a Phase B run completes end-to-end with the same map/board/log.

## Task list

### Phase 1 — Tools (deterministic foundation, no LLM)

#### Task 1: `DispatchTools` — the read tools + the guarded write tool
**Description:** The `@Tool`-annotated wrappers over the public ports/coordinator
(`listPendingOrders`, `getStock`, `listAvailableWorkers`, `assignOrderToWorker`). The write tool
catches `IllegalArgumentException`/`IllegalStateException` from the coordinator and returns an
`AssignmentOutcome` (`assigned=false`, `detail=<message>`) instead of throwing. Add the
`AssignmentOutcome` record and the `ArchitectureTest` rule that no business module depends on
`com.wms.ai.agent`.

**Acceptance criteria:**
- [ ] The three read tools return the same data as their underlying port calls (PENDING orders, a SKU's stock, IDLE workers).
- [ ] `assignOrderToWorker` tool: on success returns `assigned=true` + the created task id; on a guardrail failure returns `assigned=false` + the exception's verbatim message (does **not** throw).
- [ ] No `com.wms.ai.agent` type references any `com.wms.ai.*.internal` package; the new ArchUnit rule passes.

**Verification:**
- [ ] `@SpringBootTest @ActiveProfiles("dev")` test (no LLM): each read tool matches its port; the write tool assigns a seeded PENDING order to an IDLE worker, and reports the out-of-stock order as a skip without throwing and without corrupting state.
- [ ] `./gradlew test --tests "com.wms.ai.*"` green.

**Dependencies:** None (coordinator + ports already exist).
**Files likely touched:** `agent/DispatchTools.java`, `agent/AssignmentOutcome.java`, `src/test/.../agent/DispatchToolsTest.java`, `ArchitectureTest.java`
**Estimated scope:** M

### Phase 2 — The agent

#### Task 2: System prompt + `DispatchAgent` orchestrator
**Description:** The §5 decision policy as `classpath:/prompts/dispatcher-system.st`, and
`DispatchAgent.dispatchOnce()` building a `ChatClient` (system prompt + `DispatchTools` + an
explicit max tool-iteration cap) that runs one cycle and returns `AiDispatchResult`
(`outcomes` from the assign tool calls + the model's final `reasoning`). Add the `spring.ai`
config (api-key, model, temperature 0).

**Acceptance criteria:**
- [ ] `dispatchOnce()` runs one cycle, exposes the four tools to the model, and assigns the **single best** pending order (at most one assignment), returning an `AiDispatchResult` (the actual tool outcome(s) + the model's reasoning text).
- [ ] The loop is bounded: Spring AI `2.0.0-M6` exposes **no per-request max-iterations knob** (`ToolCallingChatOptions` only toggles `internalToolExecutionEnabled`), so the bound comes from the single-best prompt ("assign exactly one order, then stop") + `temperature: 0` + the framework's internal tool loop — it cannot recurse indefinitely.
- [ ] With no API key configured, the app still boots and existing tests stay green (beans load; only a real call needs a key).
- [ ] The system prompt encodes the ranked factors (priority → dueAt → stock → idle worker → zone affinity), instructs skipping insufficient-stock orders, and instructs choosing exactly one best order per cycle.

**Outcome capture:** `DispatchTools` records each `assignOrderToWorker` outcome; `dispatchOnce`
drains that list after the call so `AiDispatchResult.outcomes` is the *ground truth* of what was
assigned/skipped (not the model's self-report), and `reasoning` is the model's final text.

**Verification:**
- [ ] Unit test with a **mocked** `ChatModel`/`ChatClient`: a canned tool-call sequence produces the expected `AiDispatchResult`; the iteration cap is asserted.
- [ ] Key-gated integration test (`@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", ...)`, dev profile): against the seeded data, the agent assigns the URGENT in-stock order to an IDLE worker and **skips** the out-of-stock one — skipped in CI, runnable locally.

**Dependencies:** Task 1.
**Files likely touched:** `agent/DispatchAgent.java`, `agent/AiDispatchResult.java`, `src/main/resources/prompts/dispatcher-system.st`, `src/main/resources/application.yaml`, `src/test/.../agent/DispatchAgentTest.java`
**Estimated scope:** M–L

### Phase 3 — Trigger endpoint

#### Task 3: `POST /api/dispatch/ai`
**Description:** `AiDispatchController` — a thin controller delegating to `DispatchAgent.dispatchOnce()`
and returning the `AiDispatchResult`. No logic; errors map through the existing `ApiExceptionHandler`.

**Acceptance criteria:**
- [ ] `POST /api/dispatch/ai` returns 200 with `{ outcomes: [...], reasoning: "..." }`.
- [ ] The controller touches no `internal/` package and contains no decision logic.

**Verification:**
- [ ] MockMvc test with `DispatchAgent` mocked: the endpoint returns the agent's result as JSON.

**Dependencies:** Task 2.
**Files likely touched:** `web/AiDispatchController.java`, `src/test/.../web/AiDispatchControllerTest.java`
**Estimated scope:** S

> **Checkpoint — after Task 3:** the backend Phase B works. With `ANTHROPIC_API_KEY` set,
> `POST /api/dispatch/ai` runs a real cycle; the floor simulator then executes the AI's
> assignments. `./gradlew clean test` green (LLM call skipped without a key).

### Phase 4 — Console wiring (watched through the same visualization)

#### Task 4: "Run AI dispatch" control + reasoning trace in the event log
**Description:** A **Run AI dispatch** button in the console that calls `POST /api/dispatch/ai`,
appends the AI's reasoning + each `AssignmentOutcome` to the **existing** event log as a new `ai`
entry kind, then refreshes. The dispatch panel keeps its human Assign control alongside it.

**Acceptance criteria:**
- [ ] `client.aiDispatch()` POSTs `/api/dispatch/ai` and resolves the uniform result (data on 2xx; `{status,error,message}` otherwise).
- [ ] `store.runAiDispatch()` appends the reasoning and one entry per outcome (assignment or skip, verbatim detail) as `ai`/`error` events, then refreshes so the board reflects the new assignments.
- [ ] Clicking **Run AI dispatch** triggers the above; the trace renders in the event log distinctly from human actions.

**Verification:**
- [ ] Vitest: client `aiDispatch` request/parse; store `runAiDispatch` appends the trace + refreshes; `DispatchPanel` emits `ai-dispatch`; `App` routes it to the store.
- [ ] Manual (key set): `bootRun … --wms.floor.simulator.enabled=true` + `npm run dev` → click **Run AI dispatch** → the AI assigns PENDING orders (URGENT/zone-affine first, out-of-stock skipped with a logged reason), the floor walks them to SHIPPED, all visible in the console.

**Dependencies:** Task 3.
**Files likely touched:** `frontend/src/api/client.js`, `stores/useWarehouse.js`, `components/DispatchPanel.vue`, `components/EventLog.vue`, `App.vue`, plus the matching `*.test.js`
**Estimated scope:** M

> **Checkpoint — Complete:** end-to-end Phase B — one click runs the LLM dispatcher through the
> same coordinator seam; its reasoning trace shows in the event log; the floor executes the picks;
> the whole cycle is watched in the same console. `./gradlew clean test` + `npm test` green.

## Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Spring AI `2.0.0-M6` is a milestone — `ChatClient`/`@Tool` API may differ from docs | Med | Pin the version (already in `build.gradle`); keep the agent thin; verify the exact API in Task 1/2 against the resolved jars; the source-driven approach |
| A tool that throws aborts the agent loop | High | The write tool **catches** the guardrail exception and returns it as a tool result so the model skips (Task 1) — guardrail still ran, never relaxed (README §6) |
| LLM loops on tool calls / runs long | Med | Explicit max tool-iteration cap on the `ChatClient` (Task 2, README §6) |
| LLM calls are non-deterministic / cost money in tests | Med | TDD the tools + controller with no LLM; mock `ChatModel` for agent logic; gate the real round-trip behind `ANTHROPIC_API_KEY` (skipped in CI) |
| Missing API key breaks boot or CI | Med | `api-key: ${ANTHROPIC_API_KEY:}` empty default; autoconfig tolerates it at startup (current green suite proves it); only a real call needs the key — documented |
| AI re-implements orchestration / bypasses guardrails | High | Reads via tools, the single write via `assignOrderToWorker`; ArchUnit keeps `agent` off every `internal/`; review against the §3.7 boundary |

## Resolved decisions

- **Default model: `claude-haiku-4-5`** — cheapest, comfortably inside the §9 latency/cost target;
  swap to `claude-sonnet-4-6`/`claude-opus-4-7` via config for higher quality.
- **One cycle = one assignment.** `dispatchOnce()` assigns the single best pending order per call
  (the Phase B analog of one human Assign click), with the tool-iteration cap as the safety bound.

## Verification (end to end)

- `./gradlew clean test` — tool + controller tests and the mocked-agent test pass; the real-LLM
  test is skipped without a key; all existing module/coordinator/web/floor tests stay green; no
  `com.wms.ai.agent` type touches any `internal/` package (ArchUnit).
- `cd frontend && npm test` — client/store/component tests for the AI trigger + trace rendering.
- Manual, key set: `./gradlew bootRun --args='--spring.profiles.active=dev --wms.floor.simulator.enabled=true'`
  with `ANTHROPIC_API_KEY` exported, + `npm run dev`. Click **Run AI dispatch**: the agent assigns
  PENDING orders (URGENT and zone-affine first; the out-of-stock `SKU-2002` order skipped with its
  reason in the log), the floor executes each pick to SHIPPED, and the reasoning trace renders in
  the event log next to any human actions — Phase B watched through the Phase A visualization.
- This produces the artifacts README §9's Phase B checklist asks about (skips insufficient stock,
  reasonable order, zone affinity, debatable reasoning trace, latency/cost).
