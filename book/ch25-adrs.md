# Architecture Decision Records

Throughout this book, we have made dozens of choices: typestate encoding over
status enums, `Either` over exceptions, opaque types over raw UUIDs, Pekko
over Akka, sessions over JWTs. Each choice had alternatives, tradeoffs, and
reasoning. But where does that reasoning live?

If it lives only in someone's head, it is lost the moment they leave the
project. If it lives in a Slack thread, it is buried within a week. If it
lives in a commit message, no one will find it.

Architecture Decision Records (ADRs) solve this by making architectural
decisions explicit, permanent, and discoverable. In this chapter, we will
look at the ADR format, walk through every ADR in the Neon WES catalogue,
and discuss how to write your own.


## What Are ADRs?

An ADR is a short document that captures a single architectural decision.
The format was popularized by Michael Nygard in a 2011 blog post and has
since become a standard practice in software projects of all sizes. Each
ADR answers three questions:

1. **What is the context?** What problem are we solving? What forces are
   at play?
2. **What did we decide?** Which option did we choose, and why?
3. **What are the consequences?** What are the benefits and tradeoffs of
   this choice?

Neon WES uses a structured template for all ADRs:

```markdown
# {Short Title of Solved Problem and Found Solution}

## Status

{Proposed | Accepted | Rejected | Deprecated | Superseded}

## Context and Problem Statement

{Two to three sentences. Frame the problem as a question.}

## Decision Drivers

- {A desired quality, constraint, or force}

## Considered Options

- {Option 1}
- {Option 2}

## Decision Outcome

Chosen option: "{title}", because {justification}.

### Consequences

#### Positive
- {Benefit}

#### Negative
- {Tradeoff}

### Confirmation
- {How the decision is verified: test suite, code review, fitness function}
```

<small>*File: docs/decisions/adr-template.md*</small>

The template is concise by design. An ADR should take 10 to 15 minutes to
write. If it takes longer, the decision probably needs to be broken down
into smaller parts.

> **Note:** ADRs are numbered sequentially (0001, 0002, ...) and never
> deleted. If a decision is reversed, the old ADR is marked as "Deprecated"
> or "Superseded by ADR NNNN" and a new ADR explains the reversal. This
> preserves the history of reasoning, including reasoning that turned out
> to be wrong.


## The Neon WES Catalogue

All ADRs live in `docs/decisions/` and follow the naming convention
`NNNN-title-with-kebab-case.md`. As of now, the project has ten ADRs
covering domain modeling, infrastructure, error handling, frontend
technology, security, and observability. Let's walk through each one.


### ADR-0001: Typestate-Encoded Aggregates

Domain aggregates model state machines (a task moves through Planned,
Allocated, Assigned, Completed). This ADR chose typestate encoding over a
status enum with runtime checks. Each state is a distinct case class, and
transition methods exist only on valid source states. The compiler catches
illegal transitions at compile time rather than at runtime.

*See: [Chapter 4: Modeling State with Typestates](ch04-typestates.md)*

<small>*File: docs/decisions/0001-use-typestate-encoded-aggregates.md*</small>


### ADR-0002: Policy-Service-Repository Pattern

The core module orchestrates business logic across multiple aggregates. A
single operation like task completion can cascade through tasks, waves,
transport orders, and consolidation groups. This ADR chose to separate
stateless decision objects (policies) from orchestration (services) and
persistence (repositories), rather than combining everything into "fat
services." Policies are pure functions with no dependencies, trivially
testable in isolation. Services inject repositories and policies, managing
the cascade. Repositories define abstract port traits with no concrete
implementations in the domain.

*See: [Chapter 6: Policies](ch06-policies.md), [Chapter 7: Services](ch07-services.md), [Chapter 8: Repositories](ch08-repositories.md)*

<small>*File: docs/decisions/0002-use-policy-service-repository-pattern.md*</small>


### ADR-0003: Opaque Type IDs with UUID v7

Every domain entity needs an identifier. Raw `UUID` is type-unsafe: nothing
prevents passing a `WaveId` where a `TaskId` is expected. This ADR chose
Scala 3 opaque types wrapping UUID, providing compile-time type distinction
with zero runtime overhead (no boxing, no wrapper objects). UUID v7
(time-ordered epoch via the uuid-creator library) provides natural
chronological ordering and better database index performance.

*See: [Chapter 3: The Common Foundation](ch03-common-foundation.md)*

<small>*File: docs/decisions/0003-use-opaque-type-ids-with-uuid-v7.md*</small>


### ADR-0004: Either-Based Error Handling

Domain operations can fail for business reasons. This ADR chose
`Either[Error, Result]` with sealed trait error ADTs over exceptions or
`Option`. Errors are visible in type signatures, forcing callers to handle
them. Exhaustive pattern matching ensures all error cases are addressed.
Each error case class carries relevant context (the ID that was not found,
the invalid quantity). No hidden control flow from thrown exceptions.

*See: [Chapter 18: Error Handling Patterns](ch18-error-handling.md)*

<small>*File: docs/decisions/0004-use-either-based-error-handling.md*</small>


### ADR-0005: Domain-Driven sbt Modules

The WES domain has many aggregates. This ADR chose sbt multi-project builds
where each top-level directory is a domain aggregate, over a single project
organized by packages. Dependencies between modules are declared explicitly
in `build.sbt`, providing compile-time enforcement of dependency boundaries.
A module cannot accidentally import from a sibling. Incremental compilation
is faster because changing one module does not recompile others.

*See: [Chapter 2: Getting Started](ch02-getting-started.md)*

<small>*File: docs/decisions/0005-use-domain-driven-sbt-modules.md*</small>


### ADR-0006: TanStack Start React Frontend

The WES needs a web frontend for warehouse operators. This ADR chose
TanStack Start with React 19, TypeScript, and Vite over Next.js and
SvelteKit. TanStack Start provides type-safe file-based routing with an
auto-generated route tree, fast development via Vite HMR, and tight
integration with TanStack Router and TanStack Query for data fetching.
The tradeoff is a smaller ecosystem and community compared to Next.js.

*See: [Chapter 24: The Frontend](ch24-frontend.md)*

<small>*File: docs/decisions/0006-use-tanstack-start-react-frontend.md*</small>


### ADR-0007: shadcn + Base UI Component System

The frontend needs accessible, customizable UI components without a rigid
design system. This ADR chose shadcn/ui configured with Base UI primitives
(not Radix), styled with Tailwind CSS v4 and Class Variance Authority (CVA).
Components are copy-pasted into `src/components/ui/` and become fully
ownable project code. Base UI provides accessible primitives (ARIA, keyboard
navigation), and Tailwind v4 with OKLch color tokens enables perceptually
uniform theming.

*See: [Chapter 24: The Frontend](ch24-frontend.md)*

<small>*File: docs/decisions/0007-use-shadcn-base-ui-component-system.md*</small>


### ADR-0008: Session-Based Authentication

Neon WES needs authentication for all HTTP endpoints. This ADR chose
server-side sessions following The Copenhagen Book over stateless JWTs and
the pekko-http-session library. Session tokens (160 bits of entropy from
SecureRandom) are SHA-256 hashed before PostgreSQL storage. Cookies are
HttpOnly, Secure, SameSite=Lax with 30-day expiry and 15-day sliding
renewal. Passwords are hashed with Argon2id. The key benefit is immediate
session revocation on logout or security incidents, which JWT cannot
provide without a blocklist.

*See: [Chapter 13: The HTTP API](ch13-http-api.md)*

<small>*File: docs/decisions/0008-use-session-based-authentication.md*</small>


### ADR-0009: RBAC with Per-User Permission Overrides

The WES has clear role hierarchies (Admin, Supervisor, Operator, Viewer) but
occasionally needs per-user exceptions: an operator promoted to handle wave
planning, or a supervisor denied access to user management. This ADR chose
RBAC with per-user permission overrides (deny wins) over plain RBAC and
Zanzibar/ReBAC. Roles define default permission sets. Per-user overrides add
or remove individual permissions. Effective permissions are role defaults
plus or minus overrides, with deny always winning. This is implemented with
two PostgreSQL tables (`role_permissions`, `user_permission_overrides`) and
a custom Pekko HTTP directive (`requirePermission`).

*See: [Chapter 13: The HTTP API](ch13-http-api.md)*

<small>*File: docs/decisions/0009-use-rbac-with-per-user-permission-overrides.md*</small>


### ADR-0010: Structured Logging with Wide Events

Neon WES needed observability: request tracing, structured logging, and
context propagation across async boundaries. This ADR chose Logback with
logstash-logback-encoder and scala-logging over Scribe (Scala-native
logging) and OpenTelemetry auto-instrumentation. The system emits one
canonical log line per HTTP request with trace ID, method, path, status,
duration, and user ID. MDC propagation uses three layers:
`RequestLoggingDirective` (HTTP), `MdcExecutionContext` (Futures), and
`Behaviors.withMdc` (actors). Production output is structured JSON via
LogstashEncoder, directly ingestible by Loki, Elasticsearch, or Datadog.

*See: [Chapter 19: Observability and Logging](ch19-observability.md)*

<small>*File: docs/decisions/0010-use-structured-logging-with-wide-events.md*</small>


## Cross-Reference Map

For quick navigation, here is the complete mapping between ADRs and book
chapters:

| ADR | Decision | Primary Chapter |
|-----|----------|----------------|
| 0001 | Typestate-Encoded Aggregates | [Ch 4: Typestates](ch04-typestates.md) |
| 0002 | Policy-Service-Repository Pattern | [Ch 6](ch06-policies.md), [Ch 7](ch07-services.md), [Ch 8](ch08-repositories.md) |
| 0003 | Opaque Type IDs with UUID v7 | [Ch 3: Common Foundation](ch03-common-foundation.md) |
| 0004 | Either-Based Error Handling | [Ch 18: Error Handling](ch18-error-handling.md) |
| 0005 | Domain-Driven sbt Modules | [Ch 2: Getting Started](ch02-getting-started.md) |
| 0006 | TanStack Start React Frontend | [Ch 24: Frontend](ch24-frontend.md) |
| 0007 | shadcn + Base UI Component System | [Ch 24: Frontend](ch24-frontend.md) |
| 0008 | Session-Based Authentication | [Ch 13: HTTP API](ch13-http-api.md) |
| 0009 | RBAC with Permission Overrides | [Ch 13: HTTP API](ch13-http-api.md) |
| 0010 | Structured Logging with Wide Events | [Ch 19: Observability](ch19-observability.md) |


## Writing Your Own ADRs

When should you write an ADR? The rule of thumb is: if you considered more
than one option, write it down. If someone is likely to ask "why did we do
it this way?" in six months, write it down now while the reasoning is fresh.

Here are some practical guidelines:

**Keep them short.** An ADR is not a design document. It captures a
decision, not a design. Two to three sentences of context, a list of
options, a one-sentence decision, and bullet-pointed consequences. If you
are writing more than a page, consider splitting the decision.

**Be honest about tradeoffs.** Every decision has a downside. ADR-0001
acknowledges that typestate encoding creates more boilerplate than a status
enum. ADR-0003 notes that opaque types require extension methods for access.
ADR-0008 admits that server-side sessions require a database lookup per
request. Recording tradeoffs honestly helps future readers understand
whether the decision still applies when circumstances change.

**Include the confirmation section.** How will you know this decision was
implemented correctly? ADR-0008 lists specific test suites:
`PasswordHasherSuite`, `AuthenticationServiceSuite`, `AuthRoutesSuite`, and
manual curl verification. This turns the ADR from a document into a
verifiable commitment.

**Number sequentially, never delete.** ADRs are append-only. When a
decision is reversed, mark the old ADR as "Deprecated" or "Superseded by
ADR NNNN" and write a new ADR explaining why the old decision no longer
holds. The history of reasoning is as valuable as the current decision.

**Use the template.** Consistency makes ADRs scannable. When every ADR
follows the same structure, readers can quickly find the context, decision,
and consequences without reading the full text. The Neon WES template lives
at `docs/decisions/adr-template.md`.

> **Note:** Some teams also record "Rejected" ADRs for options they
> seriously considered but decided against. This prevents the same debate
> from resurfacing. If someone proposes JWTs for authentication, pointing
> them to ADR-0008 (which evaluated and rejected JWTs with detailed
> reasoning) saves everyone's time.


## ADRs and the Codebase

One question that comes up frequently: should ADRs reference code, and
should code reference ADRs?

In Neon WES, the answer to both is yes, but lightly. ADR-0001 includes a
Mermaid state diagram of the Task aggregate. ADR-0008 includes the SQL
schema for the sessions table. ADR-0010 includes log output examples. These
code fragments make the ADR self-contained; a reader does not need to go
find the source code to understand the decision.

In the other direction, some source files reference their ADR in comments:

```scala
/** Marker trait for types serialized via Jackson CBOR in the Pekko
  * journal and cluster. All actor commands, responses, state wrappers,
  * and event envelopes must mix in this trait.
  *
  * The binding to JacksonCborSerializer is declared in serialization.conf.
  */
trait CborSerializable
```

And service files reference the pattern ADR:

```scala
// Policy-Service-Repository pattern (ADR-0002)
```

These references create a navigable web between decisions and
implementations. When you encounter code and wonder why it was written that
way, the ADR reference points you to the reasoning.


## Summary

- **ADRs are short documents** that capture architectural decisions: the
  context, the options considered, the choice made, and the consequences.
- **Neon WES has ten ADRs** covering domain modeling (typestates, opaque
  type IDs, modular sbt structure), core patterns (Policy-Service-Repository,
  Either-based errors), infrastructure (sessions, RBAC, structured logging),
  and the frontend stack (TanStack Start, shadcn/ui).
- **ADRs are append-only.** Reversed decisions are superseded, never deleted.
- **Use the template.** Consistency makes ADRs scannable and useful.
- **Cross-reference.** ADRs reference code, code references ADRs, and the
  book cross-references both.


## What Comes Next

This is the final technical chapter. In Chapter 26, we will step back and
look at the complete arc of what we have built: from domain types to a
running distributed system. We will revisit the five architectural patterns
that tie the system together, point to further reading, and discuss where
Neon WES goes from here.
