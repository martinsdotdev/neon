# Error Handling Patterns

Every system must deal with things going wrong. The question is not whether
errors will happen, but how we represent them, propagate them, and present
them to callers. In Neon WES, error handling is not an afterthought bolted
onto the domain model. It is a deliberate, three-level architecture that
matches different error types to different mechanisms.

In this chapter, we will walk through all three levels: `require()` for
programming errors, sealed trait ADTs for business errors, and RFC 9457
Problem Details mapping for external callers. Along the way, we will see how
these levels compose into a coherent strategy that spans the entire system,
from aggregate creation to the JSON response body.

## Three Levels of Error Handling

Before diving into the details, let's get the big picture. Neon WES has
three distinct categories of errors, and each one is handled by a different
mechanism:

| Level | What Goes Wrong                                        | Mechanism                       | Example                                 |
| ----- | ------------------------------------------------------ | ------------------------------- | --------------------------------------- |
| 1     | Programming error: a precondition was violated         | `require()` throws              | Negative quantity, empty order list     |
| 2     | Business error: a valid request hits an invalid state  | Sealed trait ADT + `Either`     | Task not found, wave already cancelled  |
| 3     | External presentation: mapping business errors to HTTP | `ProblemMapper` + problem+json  | NotFound to 404, AlreadyTerminal to 409 |

Level 1 catches bugs. Level 2 models expected failure modes. Level 3
translates those failure modes into RFC 9457 Problem Details responses for
HTTP clients. Let's examine each one.

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

<small>_File: task/src/main/scala/neon/task/Task.scala_</small>

And `WavePlanner.plan` guards its own precondition:

```scala
require(orders.nonEmpty, "orders must not be empty")
```

<small>_File: wave/src/main/scala/neon/wave/WavePlanner.scala_</small>

The `Inventory` aggregate is particularly thorough about its preconditions:

```scala
require(quantity > 0, s"quantity must be positive, got $quantity")
require(quantity <= available,
  s"quantity $quantity exceeds available $available")
```

<small>_File: inventory/src/main/scala/neon/inventory/Inventory.scala_</small>

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
aggregate. The exception bubbles up to the global exception handler, which
renders a 500 Internal Server Error (as a problem+json body, see Level 3),
which is appropriate for a bug.

@:callout(info)

`require()` is Scala's built-in precondition method. It takes a
boolean condition and a message string. When the condition is false, it
throws `IllegalArgumentException` with the given message. Think of it as
a lightweight assertion that stays active in production.

@:@

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

<small>_File: core/src/main/scala/neon/core/TaskCompletionCascade.scala_</small>

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

<small>_File: core/src/main/scala/neon/core/WaveCancellationService.scala_</small>

Simpler. Only two things can go wrong when cancelling a wave: it does not
exist, or it is already in a terminal state (completed or cancelled). The
error type has exactly two cases.

### The Error ADT Catalogue

Neon WES has over a dozen error ADTs, one per service operation. Here is a
representative sample:

| Error ADT                    | Service                        | Cases                                                                |
| ---------------------------- | ------------------------------ | -------------------------------------------------------------------- |
| `TaskCompletionError`        | `TaskCompletionService`        | NotFound, NotAssigned, InvalidQuantity, VerificationRequired         |
| `TaskLifecycleError`         | `AsyncTaskLifecycleService`    | NotFound, InWrongState, AlreadyTerminal, UserNotFound, UserNotActive |
| `WaveCancellationError`      | `WaveCancellationService`      | NotFound, AlreadyTerminal                                            |
| `WorkstationAssignmentError` | `WorkstationAssignmentService` | NotFound, InWrongState                                               |
| `AuthError`                  | `AuthenticationService`        | InvalidCredentials, AccountInactive                                  |

The pattern repeats across transport orders, consolidation groups, slots,
inventory, stock positions, inbound deliveries, and cycle counts. Each ADT
has exactly the cases that its specific service can produce, no more, no
less.

## Either Composition in Services

Now let's see how services use `Either` to compose operations that can fail
at multiple points. Task completion is split into a pure decision module and
a thin shell: the `TaskCompletionCascade.validate` gate decides every failure
mode up front and returns either an error or a validated `Task.Assigned`:

```scala
def validate(
    taskId: TaskId,
    task: Option[Task],
    actualQuantity: Int,
    verified: Boolean,
    verificationProfile: VerificationProfile
): Either[TaskCompletionError, Task.Assigned] =
  if actualQuantity < 0 then
    Left(TaskCompletionError.InvalidActualQuantity(taskId, actualQuantity))
  else
    task match
      case None                          => Left(TaskCompletionError.TaskNotFound(taskId))
      case Some(assigned: Task.Assigned) =>
        if verificationProfile.requiresVerification(assigned.packagingLevel) && !verified
        then Left(TaskCompletionError.VerificationRequired(taskId))
        else Right(assigned)
      case Some(_) => Left(TaskCompletionError.TaskNotAssigned(taskId))
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionCascade.scala_</small>

Let's trace the logic step by step:

1. **Validate input.** If `actualQuantity` is negative, return
   `Left(InvalidActualQuantity)` immediately. This is a borderline case
   between Level 1 and Level 2; the cascade catches it as a business error
   rather than letting the aggregate's `require()` throw.

2. **Look up the task.** If the loaded task is `None`, return
   `Left(TaskNotFound)`.

3. **Check the state.** If the task exists but is not `Assigned`, return
   `Left(TaskNotAssigned)`. Here we use pattern matching on the typestate:
   only `Task.Assigned` can be completed.

4. **Check verification.** If the packaging level requires verification and
   the `verified` flag is false, return `Left(VerificationRequired)`.

5. **Proceed.** Only when all four checks pass does `validate` return
   `Right(assigned)`. The shell then loads the cascade's state and calls
   `TaskCompletionCascade.decide`, which runs the full cascade (shortpick
   check, routing, wave completion, picking completion) over a total function
   that cannot fail. The sync `TaskCompletionService` and its async sibling
   are thin load/decide/persist shells over this one module, so they cannot
   drift.

And here is `WaveCancellationService.cancel`, which follows the same
pattern:

```scala
def cancel(
    waveId: WaveId,
    at: Instant
): Either[WaveCancellationError, WaveCancellationResult] =
  waveRepository.findById(waveId) match
    case None                          => Left(WaveCancellationError.WaveNotFound(waveId))
    case Some(planned: Wave.Planned)   => cancelPlanned(planned, at)
    case Some(released: Wave.Released) => cancelReleased(released, at)
    case Some(_: Wave.Completed)       => Left(WaveCancellationError.WaveAlreadyTerminal(waveId))
    case Some(_: Wave.Cancelled)       => Left(WaveCancellationError.WaveAlreadyTerminal(waveId))
```

<small>_File: core/src/main/scala/neon/core/WaveCancellationService.scala_</small>

The structure is identical. Look up the entity, match on its state, return
`Left` for invalid states, proceed for valid ones. Both terminal states
(`Completed` and `Cancelled`) produce the same `WaveAlreadyTerminal` error.

Notice how the typestate pattern from Chapter 4 and the error ADT pattern
reinforce each other. The `match` on `Some(planned: Wave.Planned)` is a
typestate check. The `Left(WaveAlreadyTerminal)` is an error ADT case. The
two mechanisms work together: typestates tell us _which_ state the entity is
in, and the error ADT tells the _caller_ why the operation was rejected.

## Level 3: Problem Details Mapping

The third level bridges domain errors to the outside world. Rather than have
every route hand-roll a status-code match, Neon WES keeps the
error-to-response knowledge at a single seam: a `ProblemMapper` type class
with one given instance per error ADT. Each instance maps an error to a
`ProblemDetails` value, the RFC 9457 (`application/problem+json`) shape. See
ADR 0011.

```scala
trait ProblemMapper[E]:
  def toProblem(error: E): ProblemDetails

object ProblemMapper:

  /** Completes the request with the error's problem response. */
  def completeProblem[E](error: E)(using mapper: ProblemMapper[E]): Route =
    val problem = mapper.toProblem(error)
    complete(StatusCode.int2StatusCode(problem.status) -> problem)
```

<small>_File: app/src/main/scala/neon/app/http/ProblemMapper.scala_</small>

A route no longer selects a status code itself. On the `Left` branch it just
calls `completeProblem(error)`, and given resolution finds the right mapper.
Here is `TaskRoutes` completing the task-completion endpoint:

```scala
onSuccess(
  taskCompletionService.complete(taskId, request.actualQuantity, request.verified, Instant.now())
):
  case Right(result) =>
    complete(TaskCompletionResponse(/* ... */))
  case Left(error) =>
    completeProblem(error)
```

<small>_File: app/src/main/scala/neon/app/http/TaskRoutes.scala_</small>

`WaveRoutes` does exactly the same thing on its `Left` branch:
`completeProblem(error)`. The difference between the two routes is only which
`ProblemMapper` instance the compiler selects; the call site is identical.

The actual error-to-status decision lives inside each given instance. Here is
the `TaskCompletionError` mapper:

```scala
given ProblemMapper[TaskCompletionError] with
  def toProblem(error: TaskCompletionError): ProblemDetails = error match
    case TaskCompletionError.TaskNotFound(taskId) =>
      ProblemDetails.of(
        status = StatusCodes.NotFound,
        slug = "task-not-found",
        title = "Task not found",
        detail = Some(s"Task ${taskId.value} was not found")
      )
    case TaskCompletionError.TaskNotAssigned(taskId) =>
      ProblemDetails.of(
        status = StatusCodes.Conflict,
        slug = "task-not-assigned",
        title = "Task not assigned",
        detail = Some(s"Task ${taskId.value} is not in the Assigned state required for completion")
      )
    case TaskCompletionError.InvalidActualQuantity(taskId, actualQuantity) =>
      ProblemDetails.of(
        status = StatusCodes.UnprocessableEntity,
        slug = "invalid-actual-quantity",
        title = "Invalid actual quantity",
        detail = Some(s"Actual quantity $actualQuantity for task ${taskId.value} is invalid")
      )
    case TaskCompletionError.VerificationRequired(taskId) =>
      ProblemDetails.of(
        status = StatusCodes.PreconditionRequired,
        slug = "verification-required",
        title = "Verification required",
        detail = Some(s"Task ${taskId.value} requires verification before completion")
      )
```

<small>_File: app/src/main/scala/neon/app/http/ProblemMapper.scala_</small>

The status mapping convention is consistent across every error ADT in the
system:

| Domain Error Pattern                    | HTTP Status               | Semantics                                             |
| --------------------------------------- | ------------------------- | ----------------------------------------------------- |
| `XxxNotFound`                           | 404 Not Found             | The entity does not exist                             |
| `XxxAlreadyTerminal`, `XxxInWrongState` | 409 Conflict              | State machine violation                               |
| `InvalidXxx`, validation errors         | 422 Unprocessable Entity  | Structurally valid request, semantically invalid data |
| `VerificationRequired`                  | 428 Precondition Required | A business precondition was not met                   |

Because each error trait is sealed and the `match` is total, the compiler
verifies exhaustive mapping. If someone adds a new error case to
`TaskCompletionError`, the given instance will not compile until the new case
maps to a status. This is the sealed trait's killer feature: no error can
silently slip through, and no error can reach a client without a problem
body.

### The Problem Details Body

Unlike a bare status code, every error now carries a structured body. The
`ProblemDetails` record renders an RFC 9457 document: a `type` URI, the
numeric `status`, a short `title`, and an optional human-readable `detail`.

```scala
final case class ProblemDetails(
    status: Int,
    title: String,
    `type`: String = ProblemDetails.About,
    detail: Option[String] = None,
    instance: Option[String] = None
)
```

<small>_File: app/src/main/scala/neon/app/http/ProblemDetails.scala_</small>

`ProblemDetails.of(slug = "task-not-assigned", ...)` builds the `type` field
as `urn:neon:error:task-not-assigned`, so a client can branch on a stable URI
rather than parsing prose. The marshaller emits the body with the
`application/problem+json` content type. A `409` from the task-completion
endpoint therefore looks like:

```json
{
  "status": 409,
  "title": "Task not assigned",
  "type": "urn:neon:error:task-not-assigned",
  "detail": "Task 0195abc... is not in the Assigned state required for completion"
}
```

The domain error ADT is the single source of truth for this body: the `slug`,
title, and detail all derive from the specific error case and its fields
(`taskId`, `actualQuantity`). Framework-level failures that never reach a
route handler, such as malformed JSON, a missing header, or an unhandled
exception, are funnelled through the same shape by `ProblemRouteHandlers`,
the `handleRejections` / `handleExceptions` wrapper installed in `HttpServer`
(Chapter 16). Every error response a client sees, domain or infrastructural,
is a problem+json document.

## Architecture Note: Railway Oriented Programming

The error handling strategy in Neon WES follows a pattern that Scott Wlaschin
calls _Railway Oriented Programming_ (ROP). The metaphor is a two-track
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
- **Level 3 (Problem Details mapping)**: the station at the end of the line,
  where we translate the track the train arrived on into an HTTP
  problem+json response.

@:callout(info)

Wlaschin's "Railway Oriented Programming" talk and blog post
are excellent resources for understanding this pattern in depth. His
examples use F#, but the concepts map directly to Scala's `Either` and
`flatMap`. See the Further Reading section in Chapter 26 for the link.

@:@

## Summary

- **`require()`** guards domain invariants that should never be violated.
  A failing `require()` means a bug in the calling code, not a user error.
- **Sealed trait ADTs** enumerate every business failure mode for each
  service. Services return `Either[Error, Result]`, making errors visible
  in the type signature and forcing callers to handle them.
- **Problem Details mapping** translates domain errors to RFC 9457
  `application/problem+json` responses through a `ProblemMapper` given per
  error ADT; routes call `completeProblem(error)`. The sealed trait's
  exhaustive matching guarantee ensures every error case maps to a status and
  a structured body.
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
