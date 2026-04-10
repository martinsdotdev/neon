# ADR 0005: Use Domain-Driven sbt Multi-Project Structure

## Status

Accepted

## Context

The WES domain has many aggregates (waves, tasks, transport orders, etc.). The question is how to organize code boundaries.

**Options considered:**
1. **Single project with packages**: All code in one sbt project, organized by package.
2. **Multi-project by domain aggregate**: Each aggregate gets its own sbt subproject with explicit dependencies.

## Decision

Use sbt multi-project builds where each top-level directory is a domain aggregate. Dependencies between modules are declared explicitly in `build.sbt`.

```
core → {wave, task, consolidation-group, ...} → common
```

Standalone modules (order, sku, user, inventory) depend only on `common` and have no cross-domain dependencies.

## Consequences

**Benefits:**
- Compile-time enforcement of dependency boundaries: a module cannot accidentally import from a sibling
- Faster incremental compilation: changing `slot` doesn't recompile `wave`
- Clear ownership: each module has a single aggregate with its events, repository trait, and tests
- The dependency graph is visible in `build.sbt`

**Tradeoffs:**
- More sbt configuration boilerplate
- Cross-module refactoring requires updating multiple subprojects
- Directory names use kebab-case (`consolidation-group`) while packages use concatenated names (`neon.consolidation`)
