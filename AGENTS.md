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

**Dependency graph:** `core` → `{wave, task, consolidation-group, handling-unit, transport-order, workstation, slot, location, carrier}` → `common`

Standalone modules (no cross-domain dependencies): `order`, `sku`, `user`, `inventory`.

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
- **Services**: orchestrators that inject repositories and policies, return `Either[Error, Result]`. Manage cascading state transitions across aggregates (e.g., task completion triggers shortpick check → routing → wave completion → consolidation group completion).
- **Repositories**: abstract trait ports (`findById`, `save`, etc.). No concrete implementations in this codebase; tests use in-memory mutable map implementations.

### Error Handling

Sealed trait ADTs for errors, `Either[Error, Result]` return types. No exceptions for domain logic.

### Common Module

Provides opaque type ID wrappers (UUID v7 via uuid-creator) for all entities, shared enums (`Priority`, `PackagingLevel`), and utility types (`UomHierarchy`, `Lot`).

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
- In-memory repository implementations with mutable maps and event tracking

### Frontend
- No semicolons, double quotes, 80-char line width
- `cn()` utility for Tailwind class merging (clsx + tailwind-merge)
- OKLch color tokens with light/dark mode support
