# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Neon WES is a Warehouse Execution System with a Scala 3 backend, a React/TanStack Start web frontend (`apps/web/`), and a React Native + Expo mobile client (`apps/mobile/`). The JS/TS workspace is managed via pnpm workspaces; shared types, API client, and design tokens live under `packages/`.

## Build & Test Commands

### Scala (sbt)

```bash
sbt compile              # Compile all modules
sbt test                 # Run all tests
sbt core/test            # Run tests for a single module
sbt "core/testOnly neon.core.ShortpickPolicySuite"  # Run a single test suite
sbt scalafmtAll          # Format all sources
sbt scalafmtCheckAll     # Check formatting
sbt scalafixAll          # Run scalafix (import organization)
```

### Web Frontend

```bash
pnpm install              # Install workspace dependencies (run at repo root)
pnpm dev:web              # Dev server on port 3000
pnpm --filter web build   # Production build
pnpm --filter web test    # Run Vitest tests
pnpm --filter web lint    # ESLint
pnpm --filter web format  # Prettier (no semicolons, double quotes, trailing commas)
```

### Mobile (Expo)

```bash
pnpm dev:mobile          # Metro on port 8081 (Expo dev server)
pnpm --filter mobile android   # Open in Android emulator (camera scanning)
pnpm --filter mobile ios       # Open in iOS simulator
# Camera scanning runs in the Expo dev client; a DataWedge path is planned (see apps/mobile/AGENTS.md)
```

## Architecture

### Backend: Domain-Driven Modular Design

Each top-level directory is an sbt subproject representing a domain aggregate. All depend on `common`; `core` depends on all domain modules.

**Dependency graph:** `app` → `core` → `{wave, task, consolidation-group, handling-unit, transport-order, workstation, slot, location, carrier}` → `common`

**Event-sourced modules** (14, with Pekko actors): `wave`, `task`, `consolidation-group`, `handling-unit`, `transport-order`, `workstation`, `slot`, `inventory`, `stock-position`, `handling-unit-stock`, `inbound-delivery`, `goods-receipt`, `cycle-count`, `count-task`.

**Reference data modules** (5, no Pekko): `order`, `sku`, `user`, `location`, `carrier`.

### Domain Aggregates Use Typestate Encoding

Aggregates model state machines as sealed trait hierarchies with state-specific case classes nested in companion objects. Transition methods exist only on valid source states and return `(NewState, Event)` tuples:

```scala
sealed trait Task:
  def id: TaskId
object Task:
  case class Planned(...) extends Task:
    def allocate(...): (Allocated, TaskEvent.TaskAllocated) = ...
  case class Allocated(...) extends Task:
    def assign(...): (Assigned, TaskEvent.TaskAssigned) = ...
```

Key state machines:

- **Wave:** Planned → Released → Completed | Cancelled
- **Task:** Planned → Allocated → Assigned → Completed | Cancelled
- **TransportOrder:** Pending → Confirmed | Cancelled
- **ConsolidationGroup:** Created → Picked → ReadyForWorkstation → Assigned → Completed | Cancelled

### Core Module: Policy-Service-Repository Pattern

- **Policies**: stateless decision objects returning `Option[(State, Event)]`. Pure business rules, easily testable in isolation.
- **Services**: orchestrators that inject repositories and policies, return `Either[Error, Result]` (sync) or `Future` (async). Manage cascading state transitions across aggregates (e.g., task completion triggers shortpick check, routing, wave completion, consolidation group completion). Multi-step cascades are extracted into a pure decision module (e.g. `TaskCompletionCascade` — `validate` then `decide` over pre-loaded state); the sync and async services are then thin load/decide/persist shells over it, so the two cannot drift.
- **Repositories**: abstract trait ports (`findById`, `save`, etc.). Sync traits use in-memory implementations in tests; async traits (`AsyncXxxRepository`) are implemented by `PekkoXxxRepository` classes backed by Cluster Sharding.

### Pekko Infrastructure Layer (Vertical Slice Architecture)

Each event-sourced domain module contains its own actor, async repository trait, and Pekko repository implementation. The `app` module contains routes, projections, reference data repos, and bootstrap wiring.

```
wave/
  Wave.scala, WaveEvent.scala          # Domain
  WaveActor.scala                       # Event-sourced actor
  AsyncWaveRepository.scala             # Async port trait
  PekkoWaveRepository.scala             # Cluster Sharding implementation
app/
  http/WaveRoutes.scala                 # HTTP API
  projection/WaveProjectionHandler.scala # CQRS read-side handler
```

**Event-Sourced Actors** (`XxxActor.scala`): the uniform behavior (MDC tags, `withEnforcedReplies`, persistence id from the entity key, snapshot retention every 100 events, and the received-command debug log) is assembled by `neon.common.entity.EventSourcedEntity.behavior[Command, Event, State]`; each actor supplies only its command/event handlers. Commands are sealed traits extending `CborSerializable`. State is `EmptyState | ActiveState(aggregate)`. Projections read via `eventsBySlices` (no `.withTagger`). GetState handling and the unknown-command rejection stay per-actor (the rejection message comes from `EventSourcedEntity.invalidCommandMessage`).

**Pekko Repositories** (`PekkoXxxRepository.scala`): extend `neon.common.entity.PekkoEntityRepository[Command, Aggregate]` for sharding init, `findByEntityId`, and `sequenceSaves`; each repo keeps only its per-event `save` mapping and any cross-entity queries. Cross-entity queries use the `R2dbcProjectionQueries` trait (from `common`) to query CQRS projection tables, then fan out to individual actors. `saveAll` is non-transactional: individual entity operations may succeed or fail independently.

**CQRS Projections** (`app/projection/`): `ProjectionBootstrap` initializes all projections via `ShardedDaemonProcess` with `R2dbcProjection.atLeastOnce`. Each handler upserts into read-side PostgreSQL tables (e.g., `task_by_wave`, `workstation_by_type_and_state`). Each module owns a `<X>ProjectionSchema` object (the single home for its table/column names and SQL), consulted by both the projection handler and the module's Pekko repository queries.

**HTTP Routes** (`app/http/`): Pekko HTTP directives with circe JSON marshalling (`derives Encoder.AsObject` / `derives Decoder`). `CirceSupport` provides implicit marshallers. Domain error ADTs map to RFC 9457 Problem Details via `ProblemMapper` given instances — routes call `completeProblem(error)` rather than hand-rolling status codes (ADR 0011).

**Bootstrap**: `Guardian` is the root actor. It obtains the R2DBC `ConnectionFactory`, creates `ServiceRegistry` (wires all repos and services), starts `ProjectionBootstrap`, and launches `HttpServer`.

### Serialization

Jackson CBOR via `pekko-serialization-jackson`. All commands, responses, state wrappers, and event envelopes extend `CborSerializable` (marker trait in `common`). Aggregate sealed traits (e.g., `Wave`, `Task`) carry `@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")` + `@JsonSubTypes` registering each state by name, for polymorphic snapshot deserialization. Only the aggregate root needs this — it is the one nested polymorphic field (inside the snapshotted `ActiveState`); commands, events, and state wrappers are top-level payloads disambiguated by the Pekko serialization manifest. Avoid `Id.CLASS`: it bakes fully-qualified class names into persisted snapshots (refactor-fragile, and a deserialization-gadget risk). Java serialization is disabled.

### Error Handling

Sealed trait ADTs for errors, `Either[Error, Result]` return types. No exceptions for domain logic.

### Common Module

Provides opaque type ID wrappers (UUID v7 via uuid-creator) for all entities, shared enums (`Priority`, `PackagingLevel`), utility types (`UomHierarchy`, `Lot`), `CborSerializable` marker trait, `R2dbcProjectionQueries` trait for cross-entity queries, and the `neon.common.entity` event-sourced scaffolding (`EventSourcedEntity`, `PekkoEntityRepository`) shared by every slice.

### Frontend workspace (`apps/`, `packages/`)

pnpm workspaces. Two apps:
- **`apps/web/`** — TanStack Start + React 19 + TypeScript. UI with shadcn/ui (Base UI primitives + CVA variants + Tailwind CSS v4). File-based routing in `src/routes/`. Path alias `@/*` maps to `apps/web/src/*`.
- **`apps/mobile/`** — Expo SDK 53+ with Expo Router v4. React Native + TypeScript. Theming via `react-native-unistyles`. Auth via Bearer token in `expo-secure-store`. Scanning currently uses `expo-camera`; a DataWedge adapter for rugged Android is planned — the scanner seam will be introduced when that second adapter lands (one adapter today, so no seam yet).

Shared packages:
- **`packages/domain/`** — the single home for TS types mirroring the Scala domain (Task, Wave, ConsolidationGroup, Permission, Location, Sku, etc.), Zod schemas, label maps, legal-transition tables. Both apps import shared entity types from here rather than re-declaring them. The Scala `PermissionContractSuite` (in `common`) reads `PERMISSION_KEYS` out of `auth.ts` and fails the build if the TS and Scala permission lists drift.
- **`packages/client/`** — `createApiClient({ baseUrl, getAuthToken })` factory returning `ResultAsync<T, ApiError>` (neverthrow). Web calls it with `getAuthToken: () => undefined` (cookie auth); mobile passes a SecureStore-backed getter. Queries/mutations consume the result through `unwrapForQuery` (`@neon/client/query`), which throws `ApiRequestError` on failure unless given a fallback; web passes `import.meta.env.DEV ? MOCK : undefined` so production surfaces errors while dev serves mocks.
- **`packages/tokens/`** — OKLch design tokens as a JS object. Web continues using CSS vars (generated from the JS object); mobile consumes the object directly via Unistyles.

## Coding Conventions

### Scala

- Scala 3.8.2, max line width 100 (scalafmt)
- Scalafix enforces organized imports (`OrganizeImports` with `Merge` grouping)
- Unabbreviated identifiers: no abbreviations in names or test descriptions
- Package structure: `neon.<module>` (e.g., `neon.core`, `neon.wave`, `neon.location`)
- Directory names use kebab-case (`consolidation-group`), packages use concatenated names (`neon.consolidationgroup`)
- `require()` for precondition validation on aggregate creation
- **Named arguments**: when calling project-defined methods/constructors/factories, name **all** arguments if the call (a) passes two or more parameters of the same type, (b) passes a boolean or bare numeric literal positionally, or (c) has five or more arguments. Name every argument, not just the ambiguous ones. Excludes `.copy(...)`, higher-order/single-lambda calls (`map`, `filter`, `fold`, `ask`), and third-party/standard-library calls (e.g. Pekko `snapshotEvery`).
- Immutable collections (List preferred)

### Tests

- ScalaTest `AnyFunSpec` with `describe`/`it` blocks
- Suite naming: `<ComponentName>Suite` (e.g., `TaskCompletionServiceSuite`)
- Mix-in traits: `OptionValues`, `EitherValues`
- Factory methods for test data setup, shared via the `DomainFactories` trait (`def assignedTask(...)`, `def releasedWave(...)`)
- In-memory repository implementations with mutable maps and event tracking for domain tests — shared `InMemoryXxxRepository` classes (sync) plus call-recording async variants for shell suites; app route suites share auth scaffolding via `RouteSuiteBase`
- `EventSourcedBehaviorTestKit` for actor tests (no cluster needed, serialization verification enabled)
- `ScalaTestWithActorTestKit` with single-node cluster for Pekko repository integration tests
- `ScalatestRouteTest` with stub services for HTTP route tests

### Frontend

- No semicolons, double quotes, 80-char line width
- `cn()` utility for Tailwind class merging (clsx + tailwind-merge)
- OKLch color tokens with light/dark mode support
