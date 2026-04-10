# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Neon WES is a Warehouse Execution System with a Scala 3 backend and a React/TanStack Start frontend (`web/`).

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
cd web
bun install              # Install dependencies
bun run dev              # Dev server on port 3000
bun run build            # Production build
bun run test             # Run Vitest tests
bun run lint             # ESLint
bun run format           # Prettier (no semicolons, double quotes, trailing commas)
```

## Architecture

### Backend: Domain-Driven Modular Design

Each top-level directory is an sbt subproject representing a domain aggregate. All depend on `common`; `core` depends on all domain modules.

**Dependency graph:** `app` → `core` → `{wave, task, consolidation-group, handling-unit, transport-order, workstation, slot, location, carrier}` → `common`

**Event-sourced modules** (8, with Pekko actors): `wave`, `task`, `consolidation-group`, `handling-unit`, `transport-order`, `workstation`, `slot`, `inventory`.

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
- **Services**: orchestrators that inject repositories and policies, return `Either[Error, Result]` (sync) or `Future` (async). Manage cascading state transitions across aggregates (e.g., task completion triggers shortpick check, routing, wave completion, consolidation group completion).
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

**Event-Sourced Actors** (`XxxActor.scala`): `EventSourcedBehavior.withEnforcedReplies` with `Command`/`ActorEvent`/`State` type parameters. Commands are sealed traits extending `CborSerializable`. State is `EmptyState | ActiveState(aggregate)`. Command handlers return `ReplyEffect`, event handlers reconstruct state for recovery. Tagged for projection consumption (`.withTagger`), snapshots every 100 events (`.withRetention`).

**Pekko Repositories** (`PekkoXxxRepository.scala`): Single-entity operations use `sharding.entityRefFor(...).ask(...)`. Cross-entity queries use `R2dbcProjectionQueries` trait (from `common`) to query CQRS projection tables, then fan out to individual actors. `saveAll` is non-transactional: individual entity operations may succeed or fail independently.

**CQRS Projections** (`app/projection/`): `ProjectionBootstrap` initializes all projections via `ShardedDaemonProcess` with `R2dbcProjection.exactlyOnce`. Each handler consumes tagged events and upserts into read-side PostgreSQL tables (e.g., `task_by_wave`, `workstation_by_type_and_state`).

**HTTP Routes** (`app/http/`): Pekko HTTP directives with circe JSON marshalling (`derives Encoder.AsObject` / `derives Decoder`). `CirceSupport` provides implicit marshallers. Domain error ADTs map to HTTP status codes.

**Bootstrap**: `Guardian` is the root actor. It obtains the R2DBC `ConnectionFactory`, creates `ServiceRegistry` (wires all repos and services), starts `ProjectionBootstrap`, and launches `HttpServer`.

### Serialization

Jackson CBOR via `pekko-serialization-jackson`. All commands, responses, state wrappers, and event envelopes extend `CborSerializable` (marker trait in `common`). Aggregate sealed traits (e.g., `Wave`, `Task`) require `@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)` for polymorphic snapshot deserialization. Java serialization is disabled.

### Error Handling

Sealed trait ADTs for errors, `Either[Error, Result]` return types. No exceptions for domain logic.

### Common Module

Provides opaque type ID wrappers (UUID v7 via uuid-creator) for all entities, shared enums (`Priority`, `PackagingLevel`), utility types (`UomHierarchy`, `Lot`), `CborSerializable` marker trait, and `R2dbcProjectionQueries` trait for cross-entity queries.

### Frontend (web/)

TanStack Start + React 19 + TypeScript. UI with shadcn/ui (Base UI primitives + CVA variants + Tailwind CSS v4). File-based routing in `src/routes/`. Path alias `@/*` maps to `src/*`.

## Coding Conventions

### Scala
- Scala 3.8.2, max line width 100 (scalafmt)
- Scalafix enforces organized imports (`OrganizeImports` with `Merge` grouping)
- Unabbreviated identifiers: no abbreviations in names or test descriptions
- Package structure: `neon.<module>` (e.g., `neon.core`, `neon.wave`, `neon.location`)
- Directory names use kebab-case (`consolidation-group`), packages use concatenated names (`neon.consolidationgroup`)
- `require()` for precondition validation on aggregate creation
- Immutable collections (List preferred)

### Tests
- ScalaTest `AnyFunSpec` with `describe`/`it` blocks
- Suite naming: `<ComponentName>Suite` (e.g., `TaskCompletionServiceSuite`)
- Mix-in traits: `OptionValues`, `EitherValues`
- Factory methods for test data setup (`def assignedTask(...)`, `def releasedWave(...)`)
- In-memory repository implementations with mutable maps and event tracking for domain tests
- `EventSourcedBehaviorTestKit` for actor tests (no cluster needed, serialization verification enabled)
- `ScalaTestWithActorTestKit` with single-node cluster for Pekko repository integration tests
- `ScalatestRouteTest` with stub services for HTTP route tests

### Frontend
- No semicolons, double quotes, 80-char line width
- `cn()` utility for Tailwind class merging (clsx + tailwind-merge)
- OKLch color tokens with light/dark mode support
