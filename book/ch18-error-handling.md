# Error Handling Patterns

Every system must deal with things going wrong. The question is not whether
errors will happen, but how we represent them, propagate them, and present
them to callers. In Neon WES, error handling is not an afterthought bolted
onto the domain model. It is a deliberate, three-level architecture that
matches different error types to different mechanisms.

In this chapter, we will walk through all three levels: `require()` for
programming errors, sealed trait ADTs for business errors, and HTTP status
code mapping for external callers. Along the way, we will see how these
levels compose into a coherent strategy that spans the entire system, from
aggregate creation to the JSON response body.


## Three Levels of Error Handling

Before diving into the details, let's get the big picture. Neon WES has
three distinct categories of errors, and each one is handled by a different
mechanism:

| Level | What Goes Wrong | Mechanism | Example |
|-------|----------------|-----------|---------|
| 1 | Programming error: a precondition was violated | `require()` throws | Negative quantity, empty order list |
| 2 | Business error: a valid request hits an invalid state | Sealed trait ADT + `Either` | Task not found, wave already cancelled |
| 3 | External presentation: mapping business errors to HTTP | Status code selection | NotFound to 404, AlreadyTerminal to 409 |

Level 1 catches bugs. Level 2 models expected failure modes. Level 3
translates those failure modes for HTTP clients. Let's examine each one.


## Level 1: Precondition Guards with `require()`

The first level of error handling is the simplest: `require()` calls on
aggregate constructors and critical operations. These guard preconditions
that should never be violated if the calling code is correct. A failing
`require()` means there is a bug in the system, not a problem with user
input.

Here is how the `Task.Planned` factory enforces its invariants:

```scala
require(requestedQuantity > 0,
  s"requestedQuantity must be positive, got $requestedQuantity")
```

<small>*File: task/src/main/scala/neon/task/Task.scala*</small>

And `WavePlanner.plan` guards its own precondition:

```scala
require(orders.nonEmpty, "orders must not be empty")
```

<small>*File: wave/src/main/scala/neon/wave/WavePlanner.scala*</small>

The `Inventory` aggregate is particularly thorough about its preconditions:

```scala
require(quantity > 0, s"quantity must be positive, got $quantity")
require(quantity <= available,
  s"quantity $quantity exceeds available $available")
```

<small>*File: inventory/src/main/scala/neon/inventory/Inventory.scala*</small>

Several patterns emerge across these examples:

**Guard at the boundary.** The `require()` calls appear at the point of
construction or the entry to a transition method. By the time a `Task.Planned`
instance exists, we know its quantity is positive. By the time `WavePlanner`
starts creating tasks, we know the order list is non-empty.

**Include context in the message.** Messages like `"requestedQuantity must be
positive, got $requestedQuantity"` tell you both what the rule is and what
the actual bad value was. When this shows up in a log, you know immediately
what happened.

**Crash, don't recover.** A `require()` failure throws an
`IllegalArgumentException`. In the domain layer, this is the correct response.
If someone passes a negative quantity to `Task.Planned`, the calling code is
wrong. The service layer should have validated this before calling the
aggregate. The exception bubbles up as a 500 Internal Server Error, which is
appropriate for a bug.

> **Note:** `require()` is Scala's built-in precondition method. It takes a
> boolean condition and a message string. When the condition is false, it
> throws `IllegalArgumentException` with the given message. Think of it as
> a lightweight assertion that stays active in production.


### When to Use `require()` vs. `Either`

The boundary between Level 1 and Level 2 is the boundary between programming
errors and business errors. Here is a simple rule of thumb:

- **If the caller could reasonably trigger this from valid user input, use
  `Either`.** A user asking to cancel an already-cancelled wave is not a
  bug. It is a legitimate request that happens to be invalid given the
  current state.

- **If the caller should have prevented this, use `require()`.** A negative
  quantity should never reach the aggregate. The service layer or HTTP
  layer should have caught it. If it arrives anyway, that is a programming
  error.

The Neon WES codebase follows this rule consistently. Preconditions that
protect aggregate invariants use `require()`. Business-level failures that
depend on runtime state use sealed trait ADTs and `Either`.


## Level 2: Sealed Trait ADTs for Business Errors

The second level is where most of the error handling lives. When a service
operation can fail for business reasons, we define a sealed trait that
enumerates every possible failure mode. Services return
`Either[Error, Result]`, forcing callers to handle both paths.

### Defining Error Types

Let's look at `TaskCompletionError`, the error type for the task completion
service:

```scala
sealed trait TaskCompletionError

object TaskCompletionError:
  case class TaskNotFound(taskId: TaskId) extends TaskCompletionError
  case class TaskNotAssigned(taskId: TaskId) extends TaskCompletionError
  case class InvalidActualQuantity(taskId: TaskId, actualQuantity: Int)
      extends TaskCompletionError
  case class VerificationRequired(taskId: TaskId) extends TaskCompletionError
```

<small>*File: core/src/main/scala/neon/core/TaskCompletionService.scala*</small>

Four things to notice:

1. **The trait is sealed.** No one outside this file can add new error cases.
   The compiler knows every possible variant, which enables exhaustive pattern
   matching.

2. **Each case class carries context.** `TaskNotFound` carries the `taskId`
   that was not found. `InvalidActualQuantity` carries both the `taskId` and
   the bad `actualQuantity`. When you log or display these errors, you have
   everything you need to understand what happened.

3. **The names are precise.** `TaskNotAssigned` does not mean the task does
   not exist (that is `TaskNotFound`). It means the task exists but is not in
   the `Assigned` state. Each name describes exactly one failure mode.

4. **No inheritance hierarchy.** The cases are flat siblings under one sealed
   trait. There is no `ValidationError` supertype or `NotFoundError` base
   class. Each error ADT is specific to its service.

Here is the corresponding error type for wave cancellation:

```scala
sealed trait WaveCancellationError

object WaveCancellationError:
  case class WaveNotFound(waveId: WaveId) extends WaveCancellationError
  case class WaveAlreadyTerminal(waveId: WaveId)
      extends WaveCancellationError
```

<small>*File: core/src/main/scala/neon/core/WaveCancellationService.scala*</small>

Simpler. Only two things can go wrong when cancelling a wave: it does not
exist, or it is already in a terminal state (completed or cancelled). The
error type has exactly two cases.

### The Error ADT Catalogue

Neon WES has over a dozen error ADTs, one per service operation. Here is a
representative sample:

| Error ADT | Service | Cases |
|-----------|---------|-------|
| `TaskCompletionError` | `TaskCompletionService` | NotFound, NotAssigned, InvalidQuantity, VerificationRequired |
| `TaskLifecycleError` | `AsyncTaskLifecycleService` | NotFound, InWrongState, AlreadyTerminal, UserNotFound, UserNotActive |
| `WaveCancellationError` | `WaveCancellationService` | NotFound, AlreadyTerminal |
| `WorkstationAssignmentError` | `WorkstationAssignmentService` | NotFound, InWrongState |
| `AuthError` | `AuthenticationService` | InvalidCredentials, AccountInactive |

The pattern repeats across transport orders, consolidation groups, slots,
inventory, stock positions, inbound deliveries, and cycle counts. Each ADT
has exactly the cases that its specific service can produce, no more, no
less.


## Either Composition in Services

Now let's see how services use `Either` to compose operations that can fail
at multiple points. Here is the `TaskCompletionService.complete` method:

```scala
def complete(
    taskId: TaskId,
    actualQuantity: Int,
    verified: Boolean,
    at: Instant
): Either[TaskCompletionError, TaskCompletionResult] =
  if actualQuantity < 0 then
    return Left(TaskCompletionError.InvalidActualQuantity(
      taskId, actualQuantity))

  taskRepository.findById(taskId) match
    case None =>
      Left(TaskCompletionError.TaskNotFound(taskId))
    case Some(assigned: Task.Assigned) =>
      if verificationProfile.requiresVerification(
        assigned.packagingLevel) && !verified
      then Left(TaskCompletionError.VerificationRequired(taskId))
      else completeAssigned(assigned, actualQuantity, at)
    case Some(_) =>
      Left(TaskCompletionError.TaskNotAssigned(taskId))
```

<small>*File: core/src/main/scala/neon/core/TaskCompletionService.scala*</small>

Let's trace the logic step by step:

1. **Validate input.** If `actualQuantity` is negative, return
   `Left(InvalidActualQuantity)` immediately. This is a borderline case
   between Level 1 and Level 2; the service catches it as a business error
   rather than letting the aggregate's `require()` throw.

2. **Look up the task.** If `findById` returns `None`, return
   `Left(TaskNotFound)`.

3. **Check the state.** If the task exists but is not `Assigned`, return
   `Left(TaskNotAssigned)`. Here we use pattern matching on the typestate:
   only `Task.Assigned` can be completed.

4. **Check verification.** If the packaging level requires verification and
   the `verified` flag is false, return `Left(VerificationRequired)`.

5. **Proceed.** Only when all four checks pass do we call
   `completeAssigned`, which runs the full cascade (shortpick check, routing,
   wave completion, picking completion) and returns `Right(result)`.

And here is `WaveCancellationService.cancel`, which follows the same
pattern:

```scala
def cancel(
    waveId: WaveId,
    at: Instant
): Either[WaveCancellationError, WaveCancellationResult] =
  waveRepository.findById(waveId) match
    case None =>
      Left(WaveCancellationError.WaveNotFound(waveId))
    case Some(planned: Wave.Planned) =>
      cancelPlanned(planned, at)
    case Some(released: Wave.Released) =>
      cancelReleased(released, at)
    case Some(_: Wave.Completed) =>
      Left(WaveCancellationError.WaveAlreadyTerminal(waveId))
    case Some(_: Wave.Cancelled) =>
      Left(WaveCancellationError.WaveAlreadyTerminal(waveId))
```

<small>*File: core/src/main/scala/neon/core/WaveCancellationService.scala*</small>

The structure is identical. Look up the entity, match on its state, return
`Left` for invalid states, proceed for valid ones. Both terminal states
(`Completed` and `Cancelled`) produce the same `WaveAlreadyTerminal` error.

Notice how the typestate pattern from Chapter 4 and the error ADT pattern
reinforce each other. The `match` on `Some(planned: Wave.Planned)` is a
typestate check. The `Left(WaveAlreadyTerminal)` is an error ADT case. The
two mechanisms work together: typestates tell us *which* state the entity is
in, and the error ADT tells the *caller* why the operation was rejected.


## Level 3: HTTP Status Code Mapping

The third level bridges domain errors to the outside world. HTTP routes
pattern-match on the `Either` result from services and select the
appropriate status code. Here is how `TaskRoutes` maps
`TaskCompletionError`:

```scala
case Left(error) =>
  error match
    case _: TaskCompletionError.TaskNotFound =>
      complete(StatusCodes.NotFound)
    case _: TaskCompletionError.TaskNotAssigned =>
      complete(StatusCodes.Conflict)
    case _: TaskCompletionError.InvalidActualQuantity =>
      complete(StatusCodes.UnprocessableEntity)
    case _: TaskCompletionError.VerificationRequired =>
      complete(StatusCodes.PreconditionRequired)
```

<small>*File: app/src/main/scala/neon/app/http/TaskRoutes.scala*</small>

And `WaveRoutes` maps `WaveCancellationError`:

```scala
case Left(_: WaveCancellationError.WaveNotFound) =>
  complete(StatusCodes.NotFound)
case Left(_: WaveCancellationError.WaveAlreadyTerminal) =>
  complete(StatusCodes.Conflict)
```

<small>*File: app/src/main/scala/neon/app/http/WaveRoutes.scala*</small>

The mapping convention is consistent across every route in the system:

| Domain Error Pattern | HTTP Status | Semantics |
|---------------------|-------------|-----------|
| `XxxNotFound` | 404 Not Found | The entity does not exist |
| `XxxAlreadyTerminal`, `XxxInWrongState` | 409 Conflict | State machine violation |
| `InvalidXxx`, validation errors | 422 Unprocessable Entity | Structurally valid request, semantically invalid data |
| `VerificationRequired` | 428 Precondition Required | A business precondition was not met |

The route layer also factors out reusable error mappers for services that
have many error cases. Here is `TaskRoutes`' lifecycle error mapper:

```scala
private def mapLifecycleError(error: TaskLifecycleError): Route =
  error match
    case _: TaskLifecycleError.TaskNotFound =>
      complete(StatusCodes.NotFound)
    case _: TaskLifecycleError.TaskInWrongState =>
      complete(StatusCodes.Conflict)
    case _: TaskLifecycleError.TaskAlreadyTerminal =>
      complete(StatusCodes.Conflict)
    case _: TaskLifecycleError.UserNotFound =>
      complete(StatusCodes.UnprocessableEntity)
    case _: TaskLifecycleError.UserNotActive =>
      complete(StatusCodes.UnprocessableEntity)
```

<small>*File: app/src/main/scala/neon/app/http/TaskRoutes.scala*</small>

Because the error traits are sealed, the compiler verifies exhaustive
matching. If someone adds a new error case to `TaskLifecycleError`, every
route that matches on it will produce a warning until the new case is
handled. This is the sealed trait's killer feature: no error can silently
slip through.


### Why No Error Bodies?

You may have noticed that the routes return status codes without JSON error
bodies. In a production system, you might add structured error responses:

```json
{
  "error": "TaskNotAssigned",
  "taskId": "0195abc...",
  "message": "Task is in Planned state, not Assigned"
}
```

The important point is that the domain error ADT would be the single source
of truth for generating these bodies. The information is already in the
error case class fields (`taskId`, `actualQuantity`). Adding JSON error
responses is a presentation concern that does not require changing the
domain or service layers.


## Architecture Note: Railway Oriented Programming

The error handling strategy in Neon WES follows a pattern that Scott Wlaschin
calls *Railway Oriented Programming* (ROP). The metaphor is a two-track
railway:

```
     Right track (success)
  =============================>
  input --> step1 --> step2 --> step3 --> output
  =============================>
     Left track (failure)
```

Every service operation is a function from input to `Either[Error, Result]`.
Think of `Either` as a two-track railway. The `Right` track carries the
success value forward through each step. The `Left` track carries the error
value, bypassing all remaining steps.

Each step in a service (repository lookup, policy call, state transition) is
what Wlaschin calls a "switch function": it takes a normal input and
produces a two-track output. The `flatMap` method (or `for`-comprehension)
chains these switches together, automatically routing `Left` values around
all subsequent processing.

Consider the task completion flow:

1. **Validate input** (switch): quantity >= 0, or Left(InvalidActualQuantity)
2. **Find task** (switch): found, or Left(TaskNotFound)
3. **Check state** (switch): is Assigned, or Left(TaskNotAssigned)
4. **Check verification** (switch): verified if required, or Left(VerificationRequired)
5. **Execute cascade** (switch): complete, shortpick, route, wave check

If any switch sends the value onto the Left track, steps 2 through 5 are
bypassed. The `Left(TaskNotFound)` from step 2 propagates directly to the
caller without executing steps 3, 4, or 5.

This is not just a metaphor. It is a structural property of the code. The
`Either` type encodes the two-track railway at the type level. The compiler
ensures that callers handle both tracks. And the sealed trait ensures that
every possible Left value is accounted for.

The three levels we discussed map onto the railway model:

- **Level 1 (`require`)**: a derailment. The train crashes. This is a bug,
  not a track switch.
- **Level 2 (sealed trait + `Either`)**: the railway itself. Every switch
  function can route to the success track or the failure track.
- **Level 3 (HTTP mapping)**: the station at the end of the line, where we
  translate the track the train arrived on into an HTTP response.

> **Note:** Wlaschin's "Railway Oriented Programming" talk and blog post
> are excellent resources for understanding this pattern in depth. His
> examples use F#, but the concepts map directly to Scala's `Either` and
> `flatMap`. See the Further Reading section in Chapter 26 for the link.


## Summary

- **`require()`** guards domain invariants that should never be violated.
  A failing `require()` means a bug in the calling code, not a user error.
- **Sealed trait ADTs** enumerate every business failure mode for each
  service. Services return `Either[Error, Result]`, making errors visible
  in the type signature and forcing callers to handle them.
- **HTTP status code mapping** translates domain errors to standard HTTP
  status codes. The sealed trait's exhaustive matching guarantee ensures
  every error case has a corresponding status code.
- **Railway Oriented Programming** provides the conceptual framework:
  `Either` is a two-track railway, `flatMap` chains switch functions, and
  `Left` values bypass all remaining processing.
- **The three levels compose cleanly.** `require()` catches bugs at the
  aggregate boundary. `Either` propagates business errors through the
  service layer. HTTP mapping presents those errors to external callers.
  Each level handles its own concern without leaking into the others.


## What Comes Next

In the next chapter, we will look at how Neon WES makes the system
observable. We will explore structured logging with wide events, MDC
propagation across async boundaries, and the three-layer instrumentation
strategy that lets us trace a single HTTP request from the route handler
through service calls and into actor message processing.
