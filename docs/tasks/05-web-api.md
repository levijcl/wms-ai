# Implementation Plan: Web / REST Layer (Module 5)

> The HTTP surface the Vue console (`06-frontend-console.md`) talks to. See `README.md` §3.5.
> It sits above the coordinator (`04-coordinator-module.md`) and the three business modules, and
> depends only on their **public ports** — never on any `internal/` package. The same endpoints
> serve the human dispatcher now and (with the AI orchestrator triggering them) Phase B later.
> The checkbox task list below doubles as the todo list.

## Context

With the coordinator providing the atomic `assignOrderToWorker` and the aggregated
`warehouseState()`, the only thing between it and a browser is a thin REST layer. This module is
plain Spring MVC controllers in a new top-level package `com.wms.ai.web`. It introduces no
business logic of its own — it deserializes requests, calls a public port, and serializes the
result (the modules' immutable records serialize cleanly via Jackson).

`spring-boot-starter-webmvc` is already on the classpath; no new dependency is needed for the API
itself. The default server port is 8080.

Two things shape the plan:

1. **The error contract must reach the browser.** The modules throw `IllegalArgumentException`
   (bad/unknown input) and `IllegalStateException` (illegal transition / insufficient stock /
   raced worker). Without handling, those become opaque 500s. A `@RestControllerAdvice` maps them
   to **400** and **409** with a JSON body so the console's event log can show *why* a dispatch
   was rejected (README §6, §7).
2. **One read endpoint drives the whole UI.** `GET /api/state` returns the coordinator's
   `WarehouseState`; the SPA polls it. Granular reads are optional conveniences.

## Confirmed decisions

- **Package `com.wms.ai.web`, controllers only.** No persistence, no `@Entity`, no `internal/`.
  It depends on `DispatchService`, `OrderService`, `OutboundService`, `InventoryService` —
  public ports exclusively. The existing per-module ArchUnit rules already forbid it from
  touching any `internal/` package (it is "outside" every module).
- **Responses reuse the module/coordinator records** (`WarehouseState`, `Order`, `Worker`,
  `PickingTask`, `Stock`, `DispatchResult`). **Requests use small local DTO records** in
  `com.wms.ai.web` (e.g. `AssignRequest(orderId, workerId)`, `StatusRequest(String status)`),
  since `NewOrder` from the Order module can be bound directly for order submission.
- **Endpoint set** (README §3.5):
  | Method & path | Delegates to |
  |---|---|
  | `GET  /api/state` | `DispatchService.warehouseState()` |
  | `POST /api/dispatch/assign` | `DispatchService.assignOrderToWorker(orderId, workerId)` |
  | `POST /api/orders` | `OrderService.submit(NewOrder)` |
  | `POST /api/orders/{id}/status` | `OrderService.updateStatus(id, OrderStatus)` |
  | `POST /api/tasks/{id}/status` | `OutboundService.updateTaskStatus(id, TaskStatus)` |
  | `POST /api/workers/{id}/status` | `OutboundService.updateWorkerStatus(id, WorkerStatus)` |
  | `POST /api/inventory/release` | `InventoryService.release(sku, quantity)` *(optional)* |
  | `GET  /api/orders`, `/api/workers`, `/api/stock` | granular reads *(optional; `state` usually suffices)* |
- **Error mapping:** `IllegalArgumentException` → **400**, `IllegalStateException` → **409**, body
  `{ "error": "<ExceptionType>", "message": "<msg>" }`. Anything else → 500 (default).
- **CORS / hosting:** in dev the SPA uses a Vite proxy (`/api → :8080`), so **no CORS config is
  needed**. A dev-profile `CorsConfiguration` allowing `http://localhost:5173` is a fallback if
  the SPA is ever run cross-origin; for a single-origin production run, the built SPA is served
  from `resources/static` (Task 4).
- **No authentication this iteration** — it is a local experiment console.

## Module layout

```
com.wms.ai.web
  StateController       GET  /api/state                       → DispatchService.warehouseState()
  DispatchController    POST /api/dispatch/assign             → DispatchService.assignOrderToWorker(...)
  OrderController       POST /api/orders, /api/orders/{id}/status   → OrderService
  OutboundController    POST /api/tasks/{id}/status, /api/workers/{id}/status → OutboundService
  InventoryController   POST /api/inventory/release           → InventoryService   (optional)
  ApiExceptionHandler   @RestControllerAdvice                 → 400 / 409 + JSON ApiError
  dto/
    AssignRequest       record (String orderId, String workerId)
    StatusRequest       record (String status)
    ReleaseRequest      record (String sku, int quantity)     (optional)
    ApiError            record (String error, String message)
  WebConfig             (dev-profile CORS / static-resource handling)   (Task 4)
```

## Architecture decisions

- **Thin controllers, zero logic.** Each handler maps HTTP ⇄ a single port call. All guardrails
  stay in the business/coordinator layer (README §2) — the web layer must never re-check or
  relax them; it just translates the resulting exception to a status code.
- **Records over the wire.** The modules already expose immutable records (entities never cross
  the boundary), so Jackson serialization needs no extra DTOs for responses. `Instant` fields
  (`dueAt`, `assignedAt`) serialize as ISO-8601 — the SPA parses them for countdowns.
- **Enum binding.** `StatusRequest` carries a `String`; the controller converts to the target
  enum (`OrderStatus` / `TaskStatus` / `WorkerStatus`), so an unknown value yields a clean **400**
  via the same advice rather than a framework deserialization error.
- **One advice for the whole API** keeps the error contract in a single place and identical for
  human clicks and (later) AI-triggered calls.

## Task list

### Phase 1 — Reads

#### Task 1: `GET /api/state` (+ optional granular reads)
**Description:** `StateController` returning the coordinator's `WarehouseState`. Optionally add
`GET /api/orders`, `/api/workers`, `/api/stock` for convenience/debugging.

**Acceptance criteria:**
- [ ] `GET /api/state` returns 200 with the four lists (stocks, orders, workers, tasks) as JSON.
- [ ] `Instant` fields serialize as ISO-8601 strings.

**Verification:**
- [ ] MockMvc test: `GET /api/state` 200 and JSON contains the seeded entities (mock the port or `@SpringBootTest` on the dev profile).

**Dependencies:** Coordinator Task 2 (`warehouseState()`).
**Files likely touched:** `web/StateController.java`, `src/test/.../web/StateControllerTest.java`
**Estimated scope:** S

### Phase 2 — Commands

#### Task 2: Dispatch + order submission + status transitions
**Description:** `DispatchController` (`POST /api/dispatch/assign`), `OrderController`
(`POST /api/orders`, `POST /api/orders/{id}/status`), `OutboundController`
(`POST /api/tasks/{id}/status`, `POST /api/workers/{id}/status`), optional `InventoryController`
(`POST /api/inventory/release`). Add the request DTO records.

**Acceptance criteria:**
- [ ] `POST /api/dispatch/assign` with `{orderId, workerId}` calls `assignOrderToWorker` and returns the `DispatchResult` (200/201).
- [ ] `POST /api/orders` binds `NewOrder` and returns the created `Order` with its id and `PENDING` status.
- [ ] The three `.../status` endpoints parse the string into the right enum and return the updated record.
- [ ] Unknown enum string → 400 (handled in Task 3).

**Verification:**
- [ ] MockMvc tests for each endpoint: happy path returns the expected record; a missing/blank field is rejected.

**Dependencies:** Task 1; Coordinator Task 3 (assign).
**Files likely touched:** `web/DispatchController.java`, `web/OrderController.java`, `web/OutboundController.java`, `web/InventoryController.java`, `web/dto/*.java`, `src/test/.../web/*ControllerTest.java`
**Estimated scope:** M

#### Task 3: `@RestControllerAdvice` — the HTTP error contract
**Description:** `ApiExceptionHandler` mapping `IllegalArgumentException` → 400 and
`IllegalStateException` → 409, each with an `ApiError(error, message)` body.

**Acceptance criteria:**
- [ ] An unknown id / bad input request returns **400** with `{error, message}`.
- [ ] An illegal transition / insufficient stock / raced worker returns **409** with `{error, message}`.
- [ ] The message is the exception's message (so the console event log can show the guardrail reason verbatim).

**Verification:**
- [ ] MockMvc tests: assign with the out-of-stock seeded order → 409 + JSON message; assign with an unknown order id → 400; an illegal order-status transition → 409.

**Dependencies:** Task 2.
**Files likely touched:** `web/ApiExceptionHandler.java`, `web/dto/ApiError.java`, `src/test/.../web/ApiExceptionHandlerTest.java`
**Estimated scope:** S

> **Checkpoint — after Task 3:** every endpoint works; guardrail failures come back as 400/409
> with a readable JSON message; tests green.

### Phase 3 — Hosting

#### Task 4: Dev CORS + static-resource serving for the built SPA
**Description:** A `@Configuration` (dev profile) allowing the Vite origin
(`http://localhost:5173`) as a fallback to the proxy, and configuration to serve the SPA's
`vite build` output from `resources/static` for a single-origin run (with SPA history-fallback to
`index.html` so client routes resolve).

**Acceptance criteria:**
- [ ] With the SPA running on the Vite dev server, `/api/*` calls succeed (proxy primary; CORS allows `:5173` as fallback).
- [ ] When a built SPA is present under `resources/static`, hitting `/` serves `index.html` and the app loads.

**Verification:**
- [ ] `./gradlew bootRun --args='--spring.profiles.active=dev'` + `npm run dev` in `frontend/`: the console loads and `GET /api/state` returns data.

**Dependencies:** Task 1.
**Files likely touched:** `web/WebConfig.java`, `src/main/resources/application.yaml` (if a profile-specific origin is configured)
**Estimated scope:** S

> **Checkpoint — Complete:** the full API is reachable from the browser; the error contract is
> consistent; `./gradlew clean test` green; the web layer touches no module `internal/` package.

## Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Guardrail failures surface as opaque 500s | High (console can't explain rejections) | `@RestControllerAdvice` maps to 400/409 + message (Task 3); explicitly tested |
| Controller re-validates or relaxes a guardrail | High (defeats the source-of-truth rule) | Thin controllers, zero logic; review + the §6 principle |
| Web layer reaching into a module `internal/` package | Med (breaks sealed port) | Depend only on public ports; existing ArchUnit rules already forbid it |
| Enum string mismatch → ugly framework error | Low | Bind as `String` in the DTO, convert in the controller → clean 400 via the advice |
| CORS friction in dev | Low | Vite proxy is primary; dev-profile CORS allow-list as fallback (Task 4) |

## Verification (end to end)

- `./gradlew test` — MockMvc tests for each endpoint and the error advice, plus all existing
  module + coordinator tests, green.
- `./gradlew bootRun --args='--spring.profiles.active=dev'` — `GET /api/state` returns the seeded
  warehouse; `POST /api/dispatch/assign` against a seeded PENDING order + IDLE worker returns a
  `DispatchResult` and a follow-up `GET /api/state` reflects the order ASSIGNED, the worker BUSY,
  and a new task; the out-of-stock order returns 409 with a readable message and leaves state
  unchanged.
- Boundary: no `com.wms.ai.web` type references any `com.wms.ai.*.internal` package.
