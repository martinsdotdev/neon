---
status: "accepted"
date: 2026-04-14
decision-makers: project owner
consulted:
informed: future contributors
---

# Use neverthrow for service-layer error handling

## Context and Problem Statement

The Neon WES frontend needs a consistent approach to error handling within the
service layer that mirrors the backend's `Either[Error, Result]` pattern. The
frontend service layer communicates with the Scala backend API, and errors must
be handled type-safely and mapped to RFC 9457 Problem Details responses. How
should the frontend represent and propagate expected failures?

## Decision Drivers

- Mirror the backend's `Either[Error, Result]` pattern for one consistent mental model.
- Make all failure modes explicit and type-checked, not hidden in thrown exceptions.
- Compose multi-step fallible operations readably.
- Map cleanly onto RFC 9457 Problem Details responses ([ADR-0011](0011-use-rfc9457-problem-details-for-errors.md)).

## Considered Options

- neverthrow with Railway Oriented Programming
- Native try/catch exceptions
- fp-ts (`Either` / `TaskEither`)

## Decision Outcome

Chosen option: "neverthrow with Railway Oriented Programming", because it
provides type-safe, composable error handling that makes all failure modes
explicit while keeping code readable.

### Rules

- Services return `Result<T, E>` or `ResultAsync<T, E>` types
- Route handlers use `.match()` to convert Results to responses
- No thrown exceptions for expected error cases in services
- Error types are discriminated unions mapped to RFC 9457 Problem Details

### Consequences

- **Good**, because `Result`/`ResultAsync` make failures explicit in the type
  signature, mirroring the backend's `Either`.
- **Good**, because errors are discriminated unions, so handling is exhaustive
  and type-checked.
- **Good**, because combinators (`.map`, `.andThen`, `.match`) compose fallible
  steps without nested try/catch.
- **Good**, because route handlers map error unions onto RFC 9457 problem types in one place.
- **Neutral**, because external or throwing APIs must be wrapped
  (`ResultAsync.fromPromise`) at the boundary.
- **Bad**, because it adds a dependency and a paradigm the whole frontend must
  adopt consistently.

### Confirmation

Confirmed in code review: service functions return `Result`/`ResultAsync`, route
handlers resolve them with `.match()`, and no expected business failure is thrown.

## Pros and Cons of the Options

### neverthrow with Railway Oriented Programming

- **Good**, because it is lightweight and purpose-built for `Result` types in TypeScript.
- **Good**, because it mirrors the backend `Either` model, keeping both sides consistent.
- **Bad**, because it introduces a functional style some contributors will need to learn.

### Native try/catch exceptions

- **Good**, because it is idiomatic JavaScript with nothing to install.
- **Bad**, because thrown errors are invisible in type signatures and easy to miss.
- **Bad**, because it diverges from the backend's value-based error model.

### fp-ts (`Either` / `TaskEither`)

- **Good**, because it is a comprehensive functional library with `Either`/`TaskEither` and more.
- **Bad**, because it is a far larger surface area and steeper learning curve than this use case warrants.
- **Bad**, because its abstractions (higher-kinded types, type classes) are heavyweight for simple service error handling.

## More Information

### Core Types

```typescript
import { ok, err, Result, ResultAsync } from "neverthrow";

const success = ok(42); // Result<number, never>
const failure = err("not found"); // Result<never, string>
```

### Key Combinators

| Method       | Purpose                          | Signature                             |
| ------------ | -------------------------------- | ------------------------------------- |
| `.map()`     | Transform success value          | `(T -> U) -> Result<U, E>`            |
| `.mapErr()`  | Transform error value            | `(E -> F) -> Result<T, F>`            |
| `.andThen()` | Chain Result-returning functions | `(T -> Result<U, E>) -> Result<U, E>` |
| `.match()`   | Handle both tracks               | `(onOk, onErr) -> U`                  |
| `combine()`  | Merge multiple Results           | `Result<T, E>[] -> Result<T[], E>`    |

### Error Type Pattern

Define discriminated unions for domain errors:

```typescript
type WaveError =
  | { type: "not_found"; waveId: string }
  | { type: "already_released" }
  | { type: "insufficient_stock"; skuId: string; available: number };

type TaskError =
  | { type: "not_found"; taskId: string }
  | { type: "invalid_state"; current: string; expected: string };

function releaseWave(waveId: string): ResultAsync<WaveRelease, WaveError>;
```

### Integration with RFC 9457

Route handlers map Result errors to Problem Details:

```typescript
const result = await waveService.release(waveId);

return result.match(
  (release) => ({ wave: release }),
  (error) => {
    switch (error.type) {
      case "not_found":
        return problemDetails(404, "not-found", `Wave ${error.waveId} not found`);
      case "already_released":
        return problemDetails(409, "conflict", "Wave already released");
      case "insufficient_stock":
        return problemDetails(422, "validation", `Insufficient stock for ${error.skuId}`);
    }
  },
);
```

### When to Use Exceptions vs Results

| Scenario                                         | Approach                            |
| ------------------------------------------------ | ----------------------------------- |
| Expected business errors (validation, not found) | `Result`                            |
| Unexpected system errors (network down)          | Throw exception                     |
| External library errors                          | Wrap in `ResultAsync.fromPromise()` |

### Related

- The backend error model this mirrors — see [ADR-0004](0004-use-either-based-error-handling.md).
- The wire format errors are mapped to — see [ADR-0011](0011-use-rfc9457-problem-details-for-errors.md).
