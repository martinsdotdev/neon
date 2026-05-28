# Development Guide

## Prerequisites

- **JDK 21+** (for Scala/sbt)
- **sbt 1.12+** (Scala build tool)
- **Node.js 20+** (frontend runtime)
- **pnpm 11+** (frontend package manager) — `corepack enable pnpm` or see https://pnpm.io/installation

## Quick Start

```bash
# Backend
sbt compile                # Compile all Scala modules
sbt test                   # Run all tests

# Frontend (from repo root)
pnpm install               # Install dependencies
pnpm dev:web               # Dev server on port 3000
```

## Backend (Scala / sbt)

### Build & Test

```bash
sbt compile                              # Compile all modules
sbt test                                 # Run all tests
sbt core/test                            # Run tests for a single module
sbt "core/testOnly neon.core.ShortpickPolicySuite"  # Run a single test suite
```

### Code Quality

```bash
sbt scalafmtAll            # Format all sources (max 100 columns)
sbt scalafmtCheckAll       # Check formatting without modifying
sbt scalafixAll            # Run scalafix (organize imports)
```

### Module Development

To work on a specific domain module:

```bash
sbt ~wave/compile          # Continuous compilation for wave module
sbt wave/test              # Run wave tests only
```

### Adding a New Domain Module

1. Create directory at project root (kebab-case: `my-module`)
2. Add source under `my-module/src/main/scala/neon/mymodule/`
3. Add tests under `my-module/src/test/scala/neon/mymodule/`
4. Register in `build.sbt`:
   ```scala
   lazy val myModule = project
     .in(file("my-module"))
     .dependsOn(common)
     .settings(name := "neon-my-module")
   ```
5. Add to root `aggregate(...)` list

## Frontend workspace (`apps/`, `packages/`)

pnpm workspaces: `apps/web/` (existing web app), `apps/mobile/` (Expo mobile),
and shared packages under `packages/{domain,client,tokens}`.

### Build & Test

```bash
pnpm install                      # Install workspace deps (run at repo root)
pnpm dev:web                      # Web dev server on port 3000
pnpm dev:mobile                   # Expo Metro on port 8081
pnpm --filter web build           # Production build (web)
pnpm --filter web test            # Run Vitest tests (web)
pnpm --filter mobile android      # Open mobile in Android emulator
```

### Code Quality

```bash
pnpm --filter web lint            # ESLint (web)
pnpm --filter web format          # Prettier (no semicolons, double quotes, trailing commas)
pnpm --filter web check           # Ultracite check (oxlint + oxfmt)
pnpm --filter web fix             # Ultracite auto-fix
pnpm typecheck                    # Type-check every workspace package
```

### Pre-commit Hooks

Root `lefthook.yml` runs `ultracite fix` on staged JS/TS/JSON/CSS files across
all workspaces. Install hooks with:

```bash
pnpm exec lefthook install        # Installs lefthook hooks
```

## Commit Convention

Follow conventional commits with granular, atomic commits:

```
<type>(<scope>): <description>
```

**Types**: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`
**Scopes**: module names (`core`, `wave`, `task`, `web`, etc.)

Examples:

```
feat(core): add TaskCompletionService with 5-step completion cascade
refactor(wave): rename status field to state
docs: add architecture decision records
fix(web): correct button variant for destructive actions
```
