# Architecture Decision Records

This directory holds Neon WES's **Architecture Decision Records (ADRs)**, written in [MADR 4.0](https://adr.github.io/madr/) format.

## What is an ADR?

> An Architectural Decision (AD) is a software design choice that addresses a functional or non-functional requirement that is architecturally significant.
> — MADR

ADRs capture the *why* behind decisions: the problem, the options considered, the chosen path, and the consequences. They complement the [architecture book](../book/README.md), which captures the *what* and the *how* — the design as it stands — and [`CLAUDE.md`](../../CLAUDE.md), which is the quick agent-facing reference.

Use ADRs for decisions that are:

- Architecturally significant — they shape structure, contracts, or trade-offs that future contributors must respect.
- Hard or expensive to reverse — if undoing it requires rewriting more than a single module, write an ADR.
- Multi-option — there were real alternatives we considered and rejected, and the rejection rationale is load-bearing for future readers.

Skip ADRs for:

- Implementation tactics that fit in a single file or PR description.
- Decisions trivially derivable from the codebase or `CLAUDE.md`.
- Personal style preferences.

## Naming

`NNNN-title-with-dashes.md`

- `NNNN` — consecutive four-digit number, starting at `0001`.
- `title-with-dashes` — short, lowercase, dash-separated, descriptive (≤ 8 words).
- Examples:
  - `0001-use-typestate-encoded-aggregates.md`
  - `0011-use-rfc9457-problem-details-for-errors.md`

## Status lifecycle

```
proposed  →  accepted  →  deprecated
                       →  superseded by ADR-NNNN
                       →  rejected (rare; usually deprecated)
```

A `proposed` ADR is a draft seeking review. `accepted` means it is in force. `deprecated` means we no longer apply it but the historical record stays. `superseded` links to the replacing ADR.

ADRs are append-only and date-stamped at the time of authoring. If a decision changes, **author a new ADR** that supersedes the old one — never edit an accepted ADR to reflect new thinking. The history is the point.

## Template

One template ships with this directory: [`adr-template.md`](adr-template.md), the full MADR 4.0 template with decision drivers, considered options, per-option pros and cons, consequences, and confirmation. Copy it, rename to the next number, and fill it in. Sections marked optional in the template may be removed when they do not apply.

## Categorization

This directory is currently flat. If the ADR count grows past ~30 or natural clusters emerge, we will move to subdirectories along these lines:

```
docs/decisions/
├── domain/          typestate, policy-service-repository, opaque IDs, error model, modules
├── infrastructure/  authentication, authorization, logging, HTTP error format
├── frontend/        framework, component system, service-layer errors
└── process/         commit conventions, branching workflow
```

For now: flat directory, numerically ordered.

## Relationship to the book and CLAUDE.md

The [architecture book](../book/README.md) is the *current* design — the present-tense explanation of how every layer works, with code. [`CLAUDE.md`](../../CLAUDE.md) is the condensed, agent-facing summary of conventions. ADRs are the *history* — how we got here, what we chose against, which assumptions are load-bearing. Where they overlap:

- The book and `CLAUDE.md` describe *the chosen approach*. The ADR describes *why we chose it over alternatives*.
- An ADR should link to the chapter that realizes it.
- The book does not repeat ADR rationale; it can reference an ADR for "why this and not X."

## Index

| # | Title | Status | Date |
|---|---|---|---|
| [0001](0001-use-typestate-encoded-aggregates.md) | Use typestate encoding for domain aggregates | accepted | 2026-04-10 |
| [0002](0002-use-policy-service-repository-pattern.md) | Use the policy-service-repository pattern in core | accepted | 2026-04-10 |
| [0003](0003-use-opaque-type-ids-with-uuid-v7.md) | Use opaque type IDs with UUID v7 | accepted | 2026-04-10 |
| [0004](0004-use-either-based-error-handling.md) | Use Either-based error handling with sealed trait ADTs | accepted | 2026-04-10 |
| [0005](0005-use-domain-driven-sbt-modules.md) | Use a domain-driven sbt multi-project structure | accepted | 2026-04-10 |
| [0006](0006-use-tanstack-start-react-frontend.md) | Use TanStack Start with React 19 for the frontend | accepted | 2026-04-10 |
| [0007](0007-use-shadcn-base-ui-component-system.md) | Use shadcn/ui with Base UI primitives | accepted | 2026-04-10 |
| [0008](0008-use-session-based-authentication.md) | Use server-side sessions for authentication | proposed | 2026-04-10 |
| [0009](0009-use-rbac-with-per-user-permission-overrides.md) | Use RBAC with per-user permission overrides for authorization | proposed | 2026-04-10 |
| [0010](0010-use-structured-logging-with-wide-events.md) | Use structured logging with wide events and MDC propagation | proposed | 2026-04-10 |
| [0011](0011-use-rfc9457-problem-details-for-errors.md) | Use RFC 9457 Problem Details for API errors | accepted | 2026-04-14 |
| [0012](0012-use-conventional-commits-and-branches.md) | Use Conventional Commits and branch naming | accepted | 2026-04-14 |
| [0013](0013-use-github-flow-workflow.md) | Use GitHub Flow workflow | accepted | 2026-04-14 |
| [0014](0014-use-neverthrow-for-service-errors.md) | Use neverthrow for service-layer error handling | accepted | 2026-04-14 |
| [0015](0015-extract-cascades-into-pure-decision-modules.md) | Extract multi-step cascades into pure decision modules behind thin service shells | accepted | 2026-06-11 |

When adding an ADR, append a row to this table.

## License

The MADR template itself is dual-licensed MIT / CC0-1.0 (the creator's choice). ADRs authored here adopt the project's overall license.
