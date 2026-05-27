---
status: "accepted"
date: 2026-04-10
decision-makers: project owner
consulted:
informed: future contributors
---

# Use a domain-driven sbt multi-project structure

## Context and Problem Statement

The WES domain has many aggregates — waves, tasks, transport orders, consolidation groups, and more. How do we organize the code so that module boundaries are real and enforced, rather than a convention that erodes the first time someone reaches across an aggregate?

## Decision Drivers

- Dependency boundaries should be enforced by the build, not just by convention.
- Incremental compilation should stay fast: an unrelated change should not recompile the world.
- Each aggregate should have a clear, single owner for its events, repository trait, and tests.
- The dependency graph should be visible and reviewable.

## Considered Options

- **Multi-project by domain aggregate** — each aggregate is its own sbt subproject with explicit dependencies.
- **Single project with packages** — all code in one sbt project, organized by package.

## Decision Outcome

Chosen option: **"Multi-project by domain aggregate"**, because it turns module boundaries into compile-time facts: a subproject can only see what its `build.sbt` dependencies allow. Each top-level directory is a domain aggregate, and dependencies are declared explicitly:

```
core → {wave, task, consolidation-group, ...} → common
```

Standalone modules (order, sku, user, inventory) depend only on `common` and have no cross-domain dependencies.

### Consequences

- **Good**, because dependency boundaries are compile-time enforced: a module cannot accidentally import from a sibling.
- **Good**, because incremental compilation is faster — changing `slot` does not recompile `wave`.
- **Good**, because ownership is clear: each module owns one aggregate, its events, its repository trait, and its tests.
- **Good**, because the dependency graph is visible in `build.sbt`.
- **Bad**, because there is more sbt configuration boilerplate.
- **Bad**, because cross-module refactoring touches multiple subprojects.
- **Neutral**, because directory names use kebab-case (`consolidation-group`) while packages use concatenated names (`neon.consolidationgroup`) — a small mapping to keep in mind.

### Confirmation

Enforced by the build: an import that crosses an undeclared module boundary fails to compile. The full graph is documented in [Appendix B — Dependency graph](../book/06-appendices/appendix-b-dependency-graph.md).

## Pros and Cons of the Options

### Multi-project by domain aggregate

- **Good**, because the build enforces boundaries — illegal cross-aggregate dependencies do not compile.
- **Good**, because incremental builds recompile only the affected subprojects.
- **Bad**, because it costs more build configuration and makes wide refactors span several modules.

### Single project with packages

- **Good**, because it is the simplest possible build, with no inter-module wiring.
- **Bad**, because package boundaries are advisory: nothing prevents a cross-aggregate import, so the structure decays under deadline pressure.
- **Bad**, because any change recompiles the whole project.

## More Information

- The module graph and its rationale: [Appendix B — Dependency graph](../book/06-appendices/appendix-b-dependency-graph.md).
- Architecture overview: [`docs/architecture.md`](../architecture.md), "Module Dependency Graph".
