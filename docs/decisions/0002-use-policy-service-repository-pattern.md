---
status: "accepted"
date: 2026-04-10
decision-makers: project owner
consulted:
informed: future contributors
---

# Use the policy-service-repository pattern in core

## Context and Problem Statement

The core module orchestrates business logic across multiple domain aggregates. A single operation — completing a task, say — can trigger a cascade of state transitions across tasks, waves, transport orders, and consolidation groups. How do we structure this so that the business rules stay testable in isolation, the orchestration stays readable, and persistence stays swappable between in-memory tests and production adapters?

## Decision Drivers

- Business rules must be unit-testable without a database or an actor system.
- A single entry point may cascade across several aggregates; orchestration must remain legible.
- Persistence must be swappable: in-memory maps in tests, Pekko-backed adapters in production.
- Rules should be reusable across the services that need them.

## Considered Options

- **Policy-service-repository** — separate stateless decision objects (policies) from orchestration (services) and persistence (repositories).
- **Fat services** — a single service layer that holds both the business rules and the orchestration logic.

## Decision Outcome

Chosen option: **"Policy-service-repository"**, because it isolates the part that is hardest to get right (the business rules) into pure, dependency-free functions, while keeping side effects and cascade management in a thin orchestration layer. The core module uses three distinct layers:

- **Policies** — stateless decision objects. Pure business rules returning `Option[(State, Event)]`. No dependencies on repositories or services.
- **Services** — orchestrators. Inject repositories and policies, manage cascading transitions, and return `Either[Error, Result]`.
- **Repositories** — abstract port traits defining persistence contracts (`findById`, `save`). No concrete implementations live in the domain.

### Consequences

- **Good**, because policies are trivially testable: pure functions with no dependencies.
- **Good**, because services are testable with in-memory repository implementations.
- **Good**, because business rules (policies) are reusable across services.
- **Good**, because the separation is clear: policies decide *what* should happen, services orchestrate *how*.
- **Bad**, because there are more files per feature (policy + service + repository) than a single service.
- **Neutral**, because there is indirection between where a rule is defined (policy) and where it is applied (service) — the cost of making the rule independently testable.

### Confirmation

Verified by the test suites: policy suites exercise rules as pure functions, and service suites (e.g., `TaskCompletionServiceSuite`) exercise orchestration against in-memory repositories. A policy that reaches for a repository, or a service that inlines a business rule, is visible in code review.

## Pros and Cons of the Options

### Policy-service-repository

- **Good**, because the decision logic is pure and isolated, so it is the easiest part of the system to test exhaustively.
- **Good**, because repositories are ports: tests inject in-memory maps, production injects sharded actors, and the domain never knows the difference.
- **Bad**, because it spreads one feature across three files and introduces a layer of indirection.

### Fat services

- **Good**, because everything for a feature lives in one place; fewer files, less indirection.
- **Bad**, because business rules become entangled with I/O and cascade management, so testing a rule requires standing up its dependencies.
- **Bad**, because rules are harder to reuse across services once they are embedded in orchestration code.

## More Information

- Architecture overview: [`docs/architecture.md`](../architecture.md), "Policy-Service-Repository Pattern".
- Walkthroughs: [Chapter 6 — Policies](../book/02-the-domain-model/ch06-policies.md), [Chapter 7 — Services](../book/02-the-domain-model/ch07-services.md), [Chapter 8 — Repositories](../book/02-the-domain-model/ch08-repositories.md).
