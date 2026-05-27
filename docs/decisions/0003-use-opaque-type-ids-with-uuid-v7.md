---
status: "accepted"
date: 2026-04-10
decision-makers: project owner
consulted:
informed: future contributors
---

# Use opaque type IDs with UUID v7

## Context and Problem Statement

Every domain entity needs an identifier. Using a raw `UUID` everywhere is type-unsafe: nothing stops a `WaveId` from being passed where a `TaskId` is expected, and such a mix-up compiles cleanly and fails only in production. How do we get type-distinct identifiers without paying a runtime cost for the wrapper?

## Decision Drivers

- Type safety: the compiler should reject passing one entity's ID where another's is expected.
- Zero runtime overhead: identifiers are pervasive, so wrappers must not add boxing or allocation.
- Chronological ordering: time-ordered IDs improve database index locality and give natural sort order.
- A single definition shared across every domain module.

## Considered Options

- **Opaque types** — a Scala 3 feature giving a compile-time-distinct type that erases to the underlying `UUID` at runtime.
- **Value classes** — `AnyVal` wrapper case classes; distinct types but with boxing in several common situations.
- **Raw UUIDs** — use `java.util.UUID` directly with no wrapper.

## Decision Outcome

Chosen option: **"Opaque types"**, because they provide compile-time type distinction with zero runtime overhead — the wrapper exists only for the type checker and erases to a bare `UUID`. IDs use UUID v7 (time-ordered epoch) via the `uuid-creator` library for natural chronological ordering, and are defined once in the `common` module.

```scala
opaque type TaskId = UUID
object TaskId:
  def apply(): TaskId = UuidCreator.getTimeOrderedEpoch()
  extension (id: TaskId) def value: UUID = id
```

### Consequences

- **Good**, because IDs are type-safe: a `WaveId` cannot be passed where a `TaskId` is expected.
- **Good**, because there is zero runtime overhead — no boxing, no wrapper objects.
- **Good**, because time-ordered UUIDs sort chronologically and improve index performance.
- **Good**, because defining them in `common` shares one definition across all domain modules.
- **Bad**, because opaque types require extension methods (`.value`) to reach the underlying UUID.
- **Neutral**, because adding behaviour beyond extension methods would require a different encoding — rarely needed for an identifier.

### Confirmation

Enforced by the Scala compiler: an ID-type mismatch fails to compile. The zero-overhead claim follows from opaque-type erasure (no wrapper class is generated).

## Pros and Cons of the Options

### Opaque types

- **Good**, because they are type-distinct at compile time and erase to the underlying `UUID` at runtime.
- **Good**, because they are a first-class Scala 3 feature, no library required for the wrapper itself.
- **Bad**, because access to the underlying value goes through an extension method rather than a field.

### Value classes (`AnyVal`)

- **Good**, because they give distinct types and predate opaque types.
- **Bad**, because they box in several common cases (arrays, generics, pattern matching), undermining the zero-overhead goal.

### Raw UUIDs

- **Good**, because they are the simplest option with no wrapper at all.
- **Bad**, because they are type-unsafe: any UUID is interchangeable with any other, so ID mix-ups are invisible to the compiler.

## More Information

- The shared foundation is described in [Chapter 3 — Common foundation](../book/02-the-domain-model/ch03-common-foundation.md).
- [`uuid-creator`](https://github.com/f4b6a3/uuid-creator) provides the UUID v7 (time-ordered epoch) generator.
