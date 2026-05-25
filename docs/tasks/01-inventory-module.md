# Implementation Plan: Inventory Module (Module 1)

> First of the three business modules in WMS-AI. See `README.md` for the overall design.
> This module is a dependency-free leaf — nothing else must be built before it.

## Overview

The Inventory module is the **source of truth for SKU stock**. It exposes a single port —
the `InventoryService` interface — with the API surface from README §3.1:

- `getStock(sku)` — current stock for a SKU
- `listAll()` — all stock rows
- `reserve(sku, qty)` — **atomic**, returns success/failure
- `release(sku, qty)` — restock on picking failure / order cancellation

It exposes **no REST controller** — dispatch orchestration belongs to the future AI
module. Guardrails (stock sufficiency, valid quantities) live in this service layer, never
in a prompt.

## Project-wide pattern: Feature + sealed port

Every module follows this layout, and Inventory establishes it:

```
com.wms.ai.inventory
  InventoryService          public interface — the only entry point (the "port")
  Stock                     public immutable record (sku, quantity, location) — the view
  internal/
    StockEntity             package-private @Entity (JPA never leaks past the boundary)
    StockRepository         package-private Spring Data JpaRepository
    InventoryServiceImpl    package-private @Service implementing the port
    SeedData                package-private CommandLineRunner
```

Callers (including the AI module later) depend on `InventoryService` + `Stock` only.
Everything under `internal/` is package-private, so it is unreachable from other modules
at compile time. Spring still wires package-private `@Service`/repository beans via
component scan, so this costs nothing at runtime.

## Architecture decisions

- **Sealed port.** Public surface is `InventoryService` plus the public view record
  `Stock(sku, quantity, location)` (matches README §3.1 "Exposed data"). The JPA
  `@Entity` is a *separate* package-private `StockEntity`; the impl maps
  `StockEntity → Stock` so Hibernate/JPA types never cross the boundary.
- **JPA + H2 (in-memory DB).** Real Spring Data repositories now; H2 keeps the experiment
  zero-setup. `StockRepository` stays hidden behind the port so H2 → Postgres is a later
  swap with no caller changes.
- **Atomic `reserve` via a single conditional UPDATE**, not read-then-write:
  `UPDATE StockEntity SET quantity = quantity - :qty WHERE sku = :sku AND quantity >= :qty`,
  returning rows-affected. `reserve` returns `true` iff exactly one row changed. Race-safe
  at the DB level without explicit locks — this is the README "worker no longer IDLE
  (raced)" / oversell guard.
- **`reserve` returns boolean** per README ("returns success/failure"). The AI module's
  composite `assignOrderToWorker` is what turns a `false` into the `IllegalStateException`
  the README failure-modes table describes. Invalid input (`qty <= 0`) →
  `IllegalArgumentException`.
- **No AI imports** in this module — unidirectional dependency preserved.

## Task list

### Phase 1 — Foundation

#### Task 1: Persistence setup (H2 + JPA config) + README alignment

**Description:** Make JPA actually run. The scaffold pulls in `spring-boot-starter-data-jpa`
but has no JDBC driver and no datasource config. Add H2, configure it, and align the
README with the JPA-now decision.

**Acceptance criteria:**
- [ ] `runtimeOnly 'com.h2database:h2'` added to `build.gradle`.
- [ ] `application.yaml` configures an H2 datasource and `spring.jpa.hibernate.ddl-auto`
      (`update` or `create`); H2 console optional for debugging.
- [ ] README updated: §2 documents the Feature + sealed port pattern; §1 non-goals and §7
      future-extensions no longer claim "in-memory only".

**Verification:**
- [ ] `./gradlew bootRun` starts; context loads with JPA active.
- [ ] `./gradlew test` passes (existing `contextLoads`).

**Dependencies:** None
**Files likely touched:** `build.gradle`, `src/main/resources/application.yaml`, `README.md`
**Estimated scope:** S

#### Task 2: Port + entity + repository (sealed)

**Description:** Create the module skeleton in the sealed-port shape — the public port and
view record, plus the package-private JPA entity and repository.

**Acceptance criteria:**
- [ ] Public `InventoryService` interface and public record `Stock(String sku, int quantity, String location)` in `com.wms.ai.inventory`.
- [ ] Package-private `StockEntity { @Id String sku; int quantity; String location; }` and `StockRepository extends JpaRepository<StockEntity, String>` in `com.wms.ai.inventory.internal`.

**Verification:**
- [ ] `@DataJpaTest` round-trips a `StockEntity` (save → findById).

**Dependencies:** Task 1
**Files likely touched:** `inventory/InventoryService.java`, `inventory/Stock.java`,
`inventory/internal/StockEntity.java`, `inventory/internal/StockRepository.java`,
`src/test/.../inventory/StockRepositoryTest.java`
**Estimated scope:** S

> **Checkpoint — after Task 2:** app boots with JPA; `StockEntity` persists; tests green.

### Phase 2 — Service API

#### Task 3: Read operations — `getStock(sku)`, `listAll()`

**Description:** Implement the read side of the port, mapping entities to the public record.

**Acceptance criteria:**
- [ ] Package-private `InventoryServiceImpl` (`@Service` in `internal`) implements
      `getStock(sku)` → `Optional<Stock>` (empty for unknown SKU) and `listAll()` → `List<Stock>`.
- [ ] `StockEntity → Stock` mapping done in the impl; no entity escapes the port.

**Verification:**
- [ ] Integration test: known SKU returns value, unknown SKU returns empty, `listAll` returns all rows.

**Dependencies:** Task 2
**Files likely touched:** `inventory/internal/InventoryServiceImpl.java`, matching test
**Estimated scope:** S

#### Task 4: `reserve(sku, qty)` — atomic, race-safe, guardrailed

**Description:** Implement the core reservation operation as a single conditional UPDATE so
it cannot oversell under concurrency.

**Acceptance criteria:**
- [ ] `@Modifying` conditional-update repo method returning rows-affected.
- [ ] `reserve` returns `true` only on a successful decrement; `false` for insufficient stock or unknown SKU.
- [ ] `qty <= 0` → `IllegalArgumentException`.

**Verification:**
- [ ] Tests: success, insufficient-stock = `false`, unknown-SKU = `false`, invalid qty throws.
- [ ] **Concurrency test:** N threads reserving against limited stock never oversell (sum reserved ≤ initial quantity).

**Dependencies:** Task 2 (Task 3 recommended)
**Files likely touched:** `inventory/internal/StockRepository.java` (query),
`inventory/internal/InventoryServiceImpl.java`, reserve test
**Estimated scope:** S

#### Task 5: `release(sku, qty)` — restock

**Description:** Increment stock back, used on picking failure or cancellation.

**Acceptance criteria:**
- [ ] Increments quantity for the SKU.
- [ ] `qty <= 0` → `IllegalArgumentException`.
- [ ] reserve-then-release returns stock to its original level.

**Verification:**
- [ ] Round-trip reserve → release test.

**Dependencies:** Task 2
**Files likely touched:** `inventory/internal/StockRepository.java`,
`inventory/internal/InventoryServiceImpl.java`, release test
**Estimated scope:** XS–S

> **Checkpoint — after Task 5:** full Inventory API works; concurrency test green.

### Phase 3 — Seed & polish

#### Task 6: Seed data for the experiment

**Description:** Seed sample stock so later dispatch experiments (esp. zone affinity) have
data to work with.

**Acceptance criteria:**
- [ ] Package-private `SeedData` `CommandLineRunner` (or `src/main/resources/import.sql`) seeds sample SKUs across multiple zones (e.g. `ZONE-1`, `ZONE-2`).
- [ ] Seeding is idempotent / scoped to a dev profile (does not duplicate on restart).

**Verification:**
- [ ] On startup, `listAll()` returns the seeded set.

**Dependencies:** Task 2
**Files likely touched:** `inventory/internal/SeedData.java` (or `src/main/resources/import.sql`)
**Estimated scope:** S

#### Task 7 (optional): ArchUnit boundary guard

**Description:** Lock the sealed-port pattern with an automated test so future modules
cannot reach into another module's `internal` package.

**Acceptance criteria:**
- [ ] Test asserts no type in `..inventory.internal..` is referenced from outside the module.

**Verification:**
- [ ] `./gradlew test` — the rule passes against current code.

**Dependencies:** Task 2
**Files likely touched:** `build.gradle` (ArchUnit test dep), `src/test/.../ArchitectureTest.java`
**Estimated scope:** S

> **Checkpoint — Complete:** all AC met; `./gradlew test` green; module exposes only
> `InventoryService` + `Stock`, has no AI imports, and internals don't leak.

## Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Read-then-write race in `reserve` | High (oversell) | Single conditional UPDATE; concurrency test in Task 4 |
| Spring AI 2.0.0-M6 / Boot 4 / Java 25 are bleeding-edge | Med | Inventory uses only JPA + web basics; isolate AI-version risk to the later AI module |
| H2 schema drift vs future Postgres | Low | Repository hidden behind the port; standard JPA mappings only |

## Open questions

- `ddl-auto`: `create` (fresh each boot) vs `update` — pick per how seed data is managed.
- Seed via `CommandLineRunner` vs `import.sql` — `CommandLineRunner` is easier to make
  profile-scoped and idempotent.
