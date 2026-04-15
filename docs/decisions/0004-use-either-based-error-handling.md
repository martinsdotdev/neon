# ADR 0004: Use Either-Based Error Handling with Sealed Trait ADTs

## Status

Accepted

## Context

Domain operations can fail for business reasons (task not found, invalid state, etc.). The question is how to represent and propagate these failures.

**Options considered:**

1. **Exceptions**: Familiar but invisible in type signatures. Easy to forget to handle.
2. **Either with sealed traits**: Errors are part of the return type. The compiler helps ensure all cases are handled.
3. **Option**: Simpler but loses error context (just "something failed").

## Decision

Use `Either[Error, Result]` as the return type for services. Define errors as sealed trait ADTs specific to each service:

```scala
sealed trait TaskCompletionError
case class TaskNotFound(taskId: TaskId) extends TaskCompletionError
case class TaskNotAssigned(taskId: TaskId) extends TaskCompletionError
```

## Consequences

**Benefits:**

- Errors are visible in type signatures; callers must handle them
- Exhaustive pattern matching ensures all error cases are addressed
- Error types carry relevant context (the ID that wasn't found, etc.)
- No hidden control flow from thrown exceptions

**Tradeoffs:**

- More verbose than throwing exceptions
- Composing multiple `Either` results requires `for`-comprehensions or `flatMap` chains
- Each service needs its own error ADT
