# ADR 0003: Use Opaque Type IDs with UUID v7

## Status

Accepted

## Context

Every domain entity needs an identifier. Using raw `UUID` everywhere is type-unsafe: nothing prevents passing a `WaveId` where a `TaskId` is expected.

**Options considered:**

1. **Raw UUIDs**: Simple, no boilerplate.
2. **Value classes**: Wrapper case classes with runtime overhead (boxing).
3. **Opaque types**: Scala 3 feature providing compile-time type distinction with zero runtime overhead.

## Decision

Use Scala 3 opaque types wrapping UUID. Use UUID v7 (time-ordered epoch) via the `uuid-creator` library for natural chronological ordering.

```scala
opaque type TaskId = UUID
object TaskId:
  def apply(): TaskId = UuidCreator.getTimeOrderedEpoch()
  extension (id: TaskId) def value: UUID = id
```

## Consequences

**Benefits:**

- Type-safe: cannot pass `WaveId` where `TaskId` is expected
- Zero runtime overhead: no boxing, no wrapper objects
- Time-ordered UUIDs enable natural chronological sorting and better index performance
- Defined in `common` module, shared across all domain modules

**Tradeoffs:**

- Opaque types require extension methods for access (`.value`)
- Cannot add methods beyond extensions without a wrapper
