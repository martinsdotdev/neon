# Use neverthrow for Service-Layer Error Handling

## Status

Accepted

## Context and Problem Statement

The Neon WES frontend needs a consistent approach to error handling within the
service layer that mirrors the backend's `Either[Error, Result]` pattern. The
frontend service layer communicates with the Scala backend API, and errors must
be handled type-safely and mapped to RFC 9457 Problem Details responses.

## Decision Outcome

Chosen option: "neverthrow with Railway Oriented Programming", because it
provides type-safe, composable error handling that makes all failure modes
explicit while keeping code readable.

### Rules

- Services return `Result<T, E>` or `ResultAsync<T, E>` types
- Route handlers use `.match()` to convert Results to responses
- No thrown exceptions for expected error cases in services
- Error types are discriminated unions mapped to RFC 9457 Problem Details

## Core Types

```typescript
import { ok, err, Result, ResultAsync } from "neverthrow";

const success = ok(42); // Result<number, never>
const failure = err("not found"); // Result<never, string>
```

## Key Combinators

| Method       | Purpose                          | Signature                             |
| ------------ | -------------------------------- | ------------------------------------- |
| `.map()`     | Transform success value          | `(T -> U) -> Result<U, E>`            |
| `.mapErr()`  | Transform error value            | `(E -> F) -> Result<T, F>`            |
| `.andThen()` | Chain Result-returning functions | `(T -> Result<U, E>) -> Result<U, E>` |
| `.match()`   | Handle both tracks               | `(onOk, onErr) -> U`                  |
| `combine()`  | Merge multiple Results           | `Result<T, E>[] -> Result<T[], E>`    |

## Error Type Pattern

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

## Integration with RFC 9457

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

## When to Use Exceptions vs Results

| Scenario                                         | Approach                            |
| ------------------------------------------------ | ----------------------------------- |
| Expected business errors (validation, not found) | `Result`                            |
| Unexpected system errors (network down)          | Throw exception                     |
| External library errors                          | Wrap in `ResultAsync.fromPromise()` |
