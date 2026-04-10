# ADR 0002: Use Policy-Service-Repository Pattern in Core

## Status

Accepted

## Context

The core module orchestrates business logic across multiple domain aggregates. A single operation (e.g., completing a task) can trigger cascading state transitions across tasks, waves, transport orders, and consolidation groups.

**Options considered:**
1. **Fat services**: Services contain both business rules and orchestration logic.
2. **Policy-Service-Repository**: Separate stateless decision objects (policies) from orchestration (services) and persistence (repositories).

## Decision

Use three distinct layers in the core module:

- **Policies**: Stateless decision objects. Pure business rules returning `Option[(State, Event)]`. No dependencies on repositories or services.
- **Services**: Orchestrators. Inject repositories and policies. Manage cascading transitions. Return `Either[Error, Result]`.
- **Repositories**: Abstract port traits. Define persistence contracts (`findById`, `save`). No concrete implementations in the domain.

## Consequences

**Benefits:**
- Policies are trivially testable: pure functions with no dependencies
- Services are testable with in-memory repository implementations
- Business rules (policies) are reusable across services
- Clear separation: policies decide *what* should happen, services orchestrate *how*

**Tradeoffs:**
- More files per feature (policy + service + repository vs. just a service)
- Indirection between where a rule is defined (policy) and where it's used (service)
