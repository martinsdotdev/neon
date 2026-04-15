# Development Guide

## Prerequisites

- **JDK 21+** (for Scala/sbt)
- **sbt 1.12+** (Scala build tool)
- **Bun** (frontend package manager and runtime)

## Quick Start

```bash
# Backend
sbt compile                # Compile all Scala modules
sbt test                   # Run all tests

# Frontend
cd web
bun install                # Install dependencies
bun run dev                # Dev server on port 3000
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

## Frontend (web/)

### Build & Test

```bash
cd web
bun install                # Install dependencies
bun run dev                # Dev server (port 3000)
bun run build              # Production build
bun run test               # Run Vitest tests
```

### Code Quality

```bash
bun run lint               # ESLint
bun run format             # Prettier (no semicolons, double quotes, trailing commas)
bun run check              # Ultracite check (oxlint + oxfmt)
bun run fix                # Ultracite auto-fix
bun run typecheck          # TypeScript type checking
```

### Pre-commit Hooks

Lefthook runs `ultracite fix` on staged files before each commit. Install hooks with:

```bash
bun run prepare            # Installs lefthook hooks
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
