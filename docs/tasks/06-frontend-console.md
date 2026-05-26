# Implementation Plan: Frontend Console + Visualization (Module 6)

> The dispatcher-facing **Vue 3 + Vite SPA**. See `README.md` §3.6 and §7. It talks to the REST
> layer (`05-web-api.md`) and renders the coordinator's `warehouseState()` snapshot. This console
> **is** the human dispatcher in Phase A; in Phase B it is unchanged — the AI's reasoning trace
> simply streams into the same event log. The checkbox task list below doubles as the todo list.

## Context

Everything below the browser is now in place: the coordinator exposes the atomic
`assignOrderToWorker` + the aggregated `warehouseState()`, and the REST layer exposes both over
HTTP with a readable error contract. The remaining piece is the visualization + controls that let
a human *experience and drive* dispatch (README §1, §7).

This is the project's first non-Java surface: a **Vue 3 + Vite SPA** in a new top-level
`frontend/` directory, separate from the Gradle build. It is a read-mostly UI — it polls one
snapshot endpoint and issues occasional command POSTs — so it stays deliberately small (no router
needed; a single view composed of panels). State updates are **polling-based** this iteration
(README §7); SSE is a noted future upgrade.

The design constraint that matters: the SPA must visualize **exactly** what `GET /api/state`
returns (the four lists of records) and must show **guardrail failures** (the 400/409 JSON
bodies) in the event log rather than swallowing them — the failure trace is part of the
experiment (README §6, §7).

## Confirmed decisions

- **Vue 3 + Vite**, in `frontend/` at the repo root, its own `package.json` (not wired into
  Gradle this iteration). Plain Vue 3 `<script setup>` SFCs; **no router** (single page).
- **Dev via Vite proxy:** `vite.config` proxies `/api → http://localhost:8080`, so the SPA on
  `:5173` calls same-origin `/api/...` with no CORS. Production option: `vite build` → output
  served from Spring `resources/static` (web Task 4).
- **One snapshot, polled.** A small store/composable (`useWarehouse`) fetches `GET /api/state`
  every ~1.5s into reactive state; all panels render from that single source. Commands
  (`assign`, status changes, submit order) POST, then trigger an immediate refetch so the UI
  reflects the change without waiting for the next tick.
- **State shape mirrors the backend records** — `stocks[]`, `orders[]`, `workers[]`, `tasks[]`;
  no client-side reshaping beyond grouping for display (orders by status, workers by zone).
- **Styling kept simple** — status/priority conveyed by color (a small legend); no component
  library required. Pinia optional — a composable with `ref`/`reactive` is enough at this size.
- **The event log is append-only and client-side**, recording each command and its outcome
  (success summary, or the `{error, message}` from a 4xx/409). It is the seam where Phase B's AI
  reasoning trace will render.

## Project layout

```
frontend/
  package.json            vue, vite, @vitejs/plugin-vue  (+ optional axios)
  vite.config.js          dev server + /api → :8080 proxy
  index.html
  src/
    main.js               createApp(App).mount('#app')
    App.vue               layout: WarehouseMap | OrderBoard | TaskList | DispatchPanel | EventLog
    api/client.js         fetch wrappers: getState(), assign(), submitOrder(), setOrderStatus(), setTaskStatus(), setWorkerStatus()
    stores/useWarehouse.js  reactive snapshot + ~1.5s polling + refresh(); event-log append
    components/
      WarehouseMap.vue    zones → worker cards (color by status) + stock per SKU
      OrderBoard.vue      kanban columns by OrderStatus; cards: customer, priority, dueAt countdown, items
      TaskList.vue        tasks linking order ↔ worker, with TaskStatus
      DispatchPanel.vue   select PENDING order + IDLE worker → Assign; status-advance buttons; submit-order form
      EventLog.vue        chronological action/outcome log incl. guardrail errors
      legend / shared bits
```

## Architecture decisions

- **Single source of truth = `GET /api/state`.** Panels are pure functions of the snapshot;
  there is no separate client state machine to drift from the backend. Backend guardrails remain
  authoritative — the UI never pre-disables an action to "prevent" a guardrail failure; it lets
  the call fail and shows the reason (matching README §6, and how the AI will behave).
- **Command-then-refresh** keeps the displayed state honest without optimistic updates that could
  contradict a guardrail rejection.
- **Polling, not push.** Simplest correct option at this scale; the store isolates the mechanism
  so swapping in SSE later (future extension) touches only `useWarehouse`.
- **Error surfacing is a feature.** The API client returns the parsed `{error, message}` on
  non-2xx so the store can append it to the event log; failures are never silent.
- **Separate from the Gradle build** this iteration — the frontend has its own toolchain; an
  optional `vite build` into `resources/static` (web Task 4) is the only integration point.

## Task list

### Phase 1 — Scaffold + data

#### Task 1: Vite + Vue 3 scaffold, API client, polling store
**Description:** Initialize the SPA in `frontend/`; configure the dev proxy; build the API client
and the `useWarehouse` store that polls `GET /api/state`.

**Acceptance criteria:**
- [ ] `frontend/` with `package.json`, `vite.config.js` (proxy `/api → :8080`), `index.html`, `src/main.js`, `src/App.vue`.
- [ ] `api/client.js` exposes `getState()` and the command calls; non-2xx responses resolve to a parsed `{status, error, message}`.
- [ ] `useWarehouse` exposes reactive `stocks/orders/workers/tasks`, a `refresh()`, and ~1.5s polling started on mount and stopped on unmount.

**Verification:**
- [ ] `npm install && npm run dev` in `frontend/` with the backend on the dev profile: the app loads and the store's snapshot is non-empty (seeded data visible in Vue devtools or a raw dump).

**Dependencies:** Web Task 1 (`GET /api/state`).
**Files likely touched:** `frontend/package.json`, `frontend/vite.config.js`, `frontend/index.html`, `frontend/src/main.js`, `frontend/src/App.vue`, `frontend/src/api/client.js`, `frontend/src/stores/useWarehouse.js`
**Estimated scope:** M

> **Checkpoint — after Task 1:** the SPA boots, polls `/api/state`, and holds the seeded
> warehouse in reactive state.

### Phase 2 — Visualization

#### Task 2: Read-only visualization — map, board, task list
**Description:** Build `WarehouseMap`, `OrderBoard`, and `TaskList`, all rendering from the store
snapshot.

**Acceptance criteria:**
- [ ] `WarehouseMap`: groups workers by `currentZone`; each worker card colored by status (IDLE / BUSY / OFFLINE); stock per SKU shown in its zone.
- [ ] `OrderBoard`: a column per `OrderStatus`; each card shows customer, a priority color (LOW→URGENT), a `dueAt` countdown, and the item lines.
- [ ] `TaskList`: each task shows order ↔ worker and its `TaskStatus`.
- [ ] All three update on the next poll after a backend change (no manual reload).

**Verification:**
- [ ] With the dev backend: the four seeded orders appear in PENDING; six workers appear in their zones with the right colors; the (initially empty) task list is empty.

**Dependencies:** Task 1.
**Files likely touched:** `frontend/src/components/WarehouseMap.vue`, `OrderBoard.vue`, `TaskList.vue`, shared legend/styles, `App.vue`
**Estimated scope:** M

### Phase 3 — Human controls

#### Task 3: Dispatch panel + event log (drive the system)
**Description:** Build `DispatchPanel` (the human's controls) and `EventLog`. Wire each command
to the API client, then `refresh()`, and append the outcome — success or the guardrail message —
to the event log.

**Acceptance criteria:**
- [ ] Assign: choose a PENDING order + an IDLE worker → Assign → `POST /api/dispatch/assign`; on success the order moves to ASSIGNED, the worker to BUSY, and a task appears (after refresh).
- [ ] Advance: buttons to move an order (PICKING → PICKED → SHIPPED), a task (→ DONE), and free a worker (→ IDLE), each calling the matching status endpoint.
- [ ] Submit order: a form (customer, priority, dueAt, item lines) → `POST /api/orders`; the new PENDING order appears on the board.
- [ ] Event log: every action appends a timestamped entry; a 400/409 appends the backend `{error, message}` verbatim and **state is unchanged**.

**Verification:**
- [ ] Manual, dev backend: assign the URGENT seeded order to an IDLE ZONE-1 worker → board/map update, log shows success. Then attempt to assign the **out-of-stock** order (`SKU-2002`) → log shows the 409 message, nothing changes. Attempt to assign to an already-BUSY worker → 409, unchanged.

**Dependencies:** Task 2; Web Tasks 2 & 3.
**Files likely touched:** `frontend/src/components/DispatchPanel.vue`, `EventLog.vue`, `App.vue`, `stores/useWarehouse.js` (event-log append), `api/client.js`
**Estimated scope:** M–L

> **Checkpoint — after Task 3:** a human can run a full dispatch lifecycle through the console;
> guardrail failures show clearly and harmlessly. This is Phase A working end to end.

### Phase 4 — Polish (+ optional single-origin build)

#### Task 4: Legend, states, and optional production build
**Description:** Add a priority/status legend; handle loading / empty / error (backend down)
states; tidy layout. Optionally produce a `vite build` served from Spring `resources/static`
(pairs with web Task 4).

**Acceptance criteria:**
- [ ] A legend explains status and priority colors; empty columns and a backend-unreachable banner are handled gracefully.
- [ ] (Optional) `vite build` output served by Spring loads at `http://localhost:8080/`.

**Verification:**
- [ ] Stop the backend → the SPA shows an unreachable state, not a blank/crash; restart → it recovers on the next poll.
- [ ] (Optional) built SPA loads single-origin and a dispatch round-trips.

**Dependencies:** Task 3; (optional) Web Task 4.
**Files likely touched:** `frontend/src/components/*` (legend, states), `frontend/vite.config.js` (build output dir, if integrating)
**Estimated scope:** S–M

> **Checkpoint — Complete:** the console is legible and robust; a full human dispatch cycle works
> against the dev backend; guardrail failures are visible and non-destructive; (optional) a built
> SPA runs single-origin.

## Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| UI pre-disables actions to avoid guardrail failures, hiding the real behavior | Med (misrepresents the system; diverges from how AI behaves) | Let calls fail; surface the 4xx/409 in the event log (README §6); do not gate on client-side guesses |
| Optimistic updates contradict a guardrail rejection | Med (UI shows a change that didn't happen) | Command-then-`refresh()`; no optimistic mutation |
| Polling races a just-issued command | Low | Refetch immediately after each command; ~1.5s tick is forgiving |
| `dueAt`/`assignedAt` parsing (ISO-8601) off | Low | Parse with `Date`/`Intl`; countdown derived from server timestamps |
| CORS in dev | Low | Vite proxy primary; dev-profile CORS allow-list fallback (web Task 4) |
| Frontend toolchain drift from the Java build | Low | Kept separate this iteration; single optional integration point (`vite build` → `static`) |

## Verification (end to end)

- `npm install && npm run dev` in `frontend/` with `./gradlew bootRun --args='--spring.profiles.active=dev'`:
  the console renders the seeded warehouse (4 PENDING orders, 6 workers across zones), and a full
  cycle works — assign a PENDING order to an IDLE worker (order → ASSIGNED, worker → BUSY, task
  created), then advance PICKING → PICKED → SHIPPED and the task → DONE, all reflected live.
- Guardrail behavior: assigning the out-of-stock order, a non-PENDING order, or a BUSY worker each
  shows the backend's 409 message in the event log and leaves state unchanged.
- (Optional) `vite build` served from `resources/static` loads at `:8080` and round-trips a
  dispatch single-origin.
