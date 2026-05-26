# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An experiment: replace a human warehouse dispatcher with an **AI module** that orchestrates
three independent business modules (Inventory / Order / Outbound) without those modules
knowing the AI exists. See `README.md` for the full design and the dispatch cycle. The three
business modules are built; the AI coordinator module is the planned next step and does not
exist yet.

## Commands

Gradle wrapper, Java 25 toolchain, Spring Boot 4. No separate lint step — match existing
formatting (4-space indent).

```bash
./gradlew test                  # run all tests
./gradlew clean test            # clean build + all tests (final checkpoint)
./gradlew build                 # compile + test + assemble

# a single test class or method (fully-qualified):
./gradlew test --tests "com.wms.ai.outbound.internal.OutboundServiceCreateTaskTest"
./gradlew test --tests "com.wms.ai.order.internal.OrderServiceStatusTransitionTest.updateStatusOfUnknownIdThrowsIllegalArgument"
./gradlew test --tests "com.wms.ai.*"   # a package glob

# run the app with sample data seeded (dev profile):
./gradlew bootRun --args='--spring.profiles.active=dev'
```

H2 console (dev only): `http://localhost:8080/h2-console`, JDBC URL `jdbc:h2:mem:wms`, user
`sa`, no password. Persistence is in-memory H2 with `ddl-auto: update` and
`open-in-view: false` — there is no external database.

## The architectural rule that governs everything: feature + sealed port

Every module is one package named after the feature (`com.wms.ai.inventory`, `…order`,
`…outbound`) and exposes **exactly one public port interface** (e.g. `InventoryService`,
`OrderService`, `OutboundService`) plus immutable view **records** and **enums**. The JPA
`@Entity`, the Spring Data repository, the `@Service` implementation, and the dev seed data
are all **package-private under a child `internal/` package**, so no other module can reach
them at compile time. Spring still wires package-private beans via component scan.

`ArchitectureTest` (one `@ArchTest` rule per module) fails the build if anything outside
`com.wms.ai.<module>..` depends on `com.wms.ai.<module>.internal..`. When you add a module,
add its rule.

**Consequences you must respect:**

- **JPA types never cross a module boundary.** The `@Service` impl maps `…Entity → record`
  (e.g. `OrderEntity → Order`) on the way out. Callers only ever see records.
- **Unidirectional dependency.** Business modules have zero knowledge of the AI module and
  **zero knowledge of each other** — Order and Outbound do not import Inventory or one
  another. Cross-module coordination is the (future) AI module's job alone. Outbound's
  `createTask(orderId, …)` therefore treats `orderId` as an **opaque string** and must not
  try to validate it against the Order module.
- **Guardrails live in the service layer, never in a prompt or a caller.** Stock sufficiency,
  legal status transitions, and worker availability are enforced inside the module APIs. If a
  caller (eventually the LLM) issues a wrong instruction, **let it fail — do not relax the
  check** to make the caller "succeed" (README §6).

## Per-module conventions

- **State machines are transition tables in the impl.** Order and Outbound each hold a
  `static final Map<Status, Set<Status>>` of allowed transitions; `updateStatus`-style
  methods consult it. Terminal states map to an empty set; self-transitions are illegal.
- **Error contract** (consistent across modules): invalid argument or unknown id →
  `IllegalArgumentException`; illegal state transition → `IllegalStateException`.
- **IDs:** reference data uses natural keys seeded at startup (`Stock` by SKU, `Worker` by a
  stable id like `WK-1`); runtime-created aggregates use service-generated
  `UUID.randomUUID().toString()` (`Order`, `PickingTask`).
- **Enums persist as `@Enumerated(EnumType.STRING)`** — stable across reordering, readable in
  the H2 console.
- **Reads and `open-in-view: false`:** flat entities (Inventory, Outbound) need no
  transaction to read. The Order entity owns a lazy `@ElementCollection` of items, so its
  read methods are `@Transactional(readOnly = true)` and map to records **inside** the
  transaction — otherwise `LazyInitializationException`. Mutations are `@Transactional`.
- **Dev seed data** is a `@Profile("dev")` `CommandLineRunner`, idempotent via a
  `count() > 0` guard, and never runs under the default (test) profile.

## Testing conventions

- Repository round-trips and service tests use `@DataJpaTest` + `@Import(<Impl>.class)`,
  autowiring the repository and the impl; fixtures are seeded by saving entities **directly
  via the repository** (reference data like workers is not created through the port).
- State machines are covered with `@ParameterizedTest` + `@CsvSource` over the legal moves
  and a representative set of illegal/terminal/self moves (see
  `OrderServiceStatusTransitionTest`, `OutboundServiceWorkerStatusTransitionTest`).
- Seed-data tests use `@SpringBootTest` + `@ActiveProfiles("dev")` with an **isolated**
  in-memory DB URL so they don't pollute the shared default-profile database (see
  `SeedDataTest`).
- AssertJ (`assertThat`, `assertThatThrownBy`) is the assertion library.

## Working in this repo

Implementation is planned and tracked in `docs/tasks/NN-<module>.md` (one file per module:
overview, confirmed decisions, ordered tasks with acceptance criteria, checkpoints). Work
proceeds **one task per commit**, message prefixed `feat(<module>): Task N — …` (or
`test(...)`), and each task is built test-first. Follow an existing module (Order is the
richest reference) when adding the next one.
