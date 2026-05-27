---
status: "accepted"
date: 2026-04-10
decision-makers: project owner
consulted:
informed: future contributors
---

# Use Either-based error handling with sealed trait ADTs

## Context and Problem Statement

Domain operations fail for ordinary business reasons: a task is not found, an aggregate is in the wrong state, a precondition is unmet. These are expected outcomes, not exceptional ones. How do we represent and propagate them so that callers cannot silently forget to handle a failure?

## Decision Drivers

- Failures should be visible in type signatures, so the compiler forces callers to handle them.
- Error cases should be exhaustively checked, with warnings for any unhandled case.
- Errors should carry context (which ID was not found, which state was wrong), not just a boolean.
- No hidden control flow: a failure should not unwind the stack from somewhere unexpected.

## Considered Options

- **`Either` with sealed-trait ADTs** — failures are values of a closed error type, returned as `Either[Error, Result]`.
- **Exceptions** — throw on failure; callers catch where they choose.
- **`Option`** — return `None` on failure.

## Decision Outcome

Chosen option: **"`Either` with sealed-trait ADTs"**, because it makes failure part of the return type — callers must acknowledge it — while preserving full error context and exhaustive matching. Services return `Either[Error, Result]`, with errors defined as sealed traits specific to each service:

```scala
sealed trait TaskCompletionError
case class TaskNotFound(taskId: TaskId) extends TaskCompletionError
case class TaskNotAssigned(taskId: TaskId) extends TaskCompletionError
```

### Consequences

- **Good**, because errors are visible in type signatures; callers must handle them.
- **Good**, because exhaustive pattern matching ensures every error case is addressed.
- **Good**, because error types carry relevant context (the ID that was not found, the state that was wrong).
- **Good**, because there is no hidden control flow from thrown exceptions.
- **Bad**, because it is more verbose than throwing.
- **Bad**, because composing multiple `Either` results requires `for`-comprehensions or `flatMap` chains.
- **Neutral**, because each service defines its own error ADT — boilerplate that doubles as precise, per-service documentation of how each operation can fail.

### Confirmation

Verified by service test suites, which assert on specific `Left` error values, and by the compiler's exhaustiveness checking over each sealed error trait. A thrown exception for an expected business failure is visible in code review.

## Pros and Cons of the Options

### `Either` with sealed-trait ADTs

- **Good**, because failure is part of the signature and cannot be ignored.
- **Good**, because the closed error type makes matching exhaustive and context-rich.
- **Bad**, because chaining fallible steps requires `for`/`flatMap` plumbing.

### Exceptions

- **Good**, because they are familiar and concise at the throw site.
- **Bad**, because they are invisible in type signatures and easy to forget to handle.
- **Bad**, because they introduce non-local control flow that is hard to reason about.

### `Option`

- **Good**, because it is the simplest "might fail" type.
- **Bad**, because it discards all error context: `None` says *that* it failed, never *why*.

## More Information

- Services and their error types: [Chapter 7 — Services](../book/02-the-domain-model/ch07-services.md).
- The cross-cutting error strategy: [Chapter 18 — Error handling](../book/04-system-concerns/ch18-error-handling.md).
- The frontend mirrors this pattern with neverthrow — see [ADR-0014](0014-use-neverthrow-for-service-errors.md).
