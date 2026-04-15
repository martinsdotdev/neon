# Services: Orchestrating the Domain

Policies decide what should happen. Aggregates enforce valid transitions. But
neither can read from a database, save a result, or coordinate work across
multiple entities. For that, we need _services_.

A service is an orchestrator: it loads data from repositories, calls policies,
and persists the results. We have spent four chapters building the pieces
(types, typestates, events, policies). In this chapter, we will see how those
pieces compose into real business operations.

## The Service Layer's Role

In the Policy-Service-Repository pattern, services sit in the middle. They have
three responsibilities:

1. **Read.** Load aggregates from repositories.
2. **Decide.** Pass domain values to policies and typestate transition methods.
3. **Write.** Persist new states and events back through repositories.

Services inject repository traits and policy objects through their constructors.
They return `Either[Error, Result]` for synchronous operations, or
`Future[Either[Error, Result]]` for async ones. They manage cascading state
transitions: a single service call might update a task, create a transport
order, complete a wave, and transition a consolidation group.

This creates a clean three-layer sandwich that you will see repeated throughout
the codebase:

- **Impure**: read from repositories (side effects)
- **Pure**: call policies and transition methods (no side effects)
- **Impure**: write results to repositories (side effects)

The impure layers handle the messy reality of I/O. The pure layer in the middle
is where the actual business logic lives. This sandwich is a direct consequence
of the Functional Core, Imperative Shell architecture we discussed at the end
of Chapter 6. Policies are the functional core. Services are the imperative
shell.

@:callout(info)

The Policy-Service-Repository pattern is documented in
ADR-0002.
Services are the orchestration layer: they call policies but never contain
business rules themselves.

@:@

## Error Handling with Sealed Trait ADTs

Before we look at a full service, let's examine how services communicate
failure. Consider `TaskCompletionService`. Completing a task can fail for
several distinct reasons, and we need the caller to know exactly which one
occurred. Here is the error type:

```scala
sealed trait TaskCompletionError

object TaskCompletionError:
  case class TaskNotFound(taskId: TaskId) extends TaskCompletionError
  case class TaskNotAssigned(taskId: TaskId) extends TaskCompletionError
  case class InvalidActualQuantity(taskId: TaskId, actualQuantity: Int)
      extends TaskCompletionError
  case class VerificationRequired(taskId: TaskId) extends TaskCompletionError
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

Four things to notice here.

**Sealed trait hierarchy.** The `sealed` keyword means the compiler knows every
possible subtype. When a caller pattern matches on a `TaskCompletionError`, the
compiler can warn if a case is missing. Add a new error variant, and every
unhandled match in the codebase lights up.

**Each error carries context.** `TaskNotFound` carries the `TaskId` that was not
found. `InvalidActualQuantity` carries both the ID and the offending quantity.
This is not just for logging; the HTTP layer can use these values to construct
meaningful error responses.

**No exceptions for domain logic.** The service returns `Either[TaskCompletionError, TaskCompletionResult]`. The error is a value, not a thrown
exception. It appears in the type signature, so callers cannot accidentally
ignore it. There is no hidden control flow, no stack unwinding, no catch blocks
to forget.

**One error ADT per service.** `TaskCompletionError` and
`WaveCancellationError` are separate sealed traits. Each describes exactly the
failures that can occur in its service, nothing more.

@:callout(info)

We will explore error handling patterns in depth in Chapter 18.
The `Either`-based approach and its rationale are documented in
ADR-0004.

@:@

## The Result Type

When a service succeeds, it returns a result that bundles every state transition
and event produced by the operation. Here is the result type for task
completion:

```scala
case class TaskCompletionResult(
    completed: Task.Completed,
    completedEvent: TaskEvent.TaskCompleted,
    shortpick: Option[(Task.Planned, TaskEvent.TaskCreated)],
    transportOrder: Option[
      (TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)
    ],
    waveCompletion: Option[(Wave.Completed, WaveEvent.WaveCompleted)],
    pickingCompletion: Option[
      (ConsolidationGroup.Picked,
       ConsolidationGroupEvent.ConsolidationGroupPicked)
    ],
    stockConsumption: Option[(StockPosition, StockPositionEvent)] = None
)
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

This result captures _everything_ that happened. The primary action (task
completed) is always present. The cascade effects are each `Option` because
they do not always fire: no shortpick means `shortpick = None`, no handling
unit means `transportOrder = None`, and so on. This gives callers full
visibility. The HTTP layer can choose which fields to include in a response.
The test layer can assert on exactly which cascades fired. Nothing is hidden.

## Walkthrough: TaskCompletionService

`TaskCompletionService` is the centerpiece of this chapter. A single call to
`complete()` can trigger up to five downstream effects, all coordinated within
one service method. Let's look at the constructor first, then walk through the
method step by step.

```scala
class TaskCompletionService(
    taskRepository: TaskRepository,
    waveRepository: WaveRepository,
    consolidationGroupRepository: ConsolidationGroupRepository,
    transportOrderRepository: TransportOrderRepository,
    verificationProfile: VerificationProfile,
    stockPositionRepository: Option[StockPositionRepository] = None
):
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

The constructor takes five repository traits and one configuration object.
Notice that `stockPositionRepository` is `Option`: stock management is not
required for all warehouse configurations, so when the repository is `None`,
the service simply skips the stock consumption step.

`VerificationProfile` is a lightweight configuration case class that defines
which packaging levels require scan verification:

```scala
case class VerificationProfile(requiredFor: Set[PackagingLevel]):
  def requiresVerification(packagingLevel: PackagingLevel): Boolean =
    requiredFor.contains(packagingLevel)

object VerificationProfile:
  val disabled: VerificationProfile = VerificationProfile(Set.empty)
```

<small>_File: core/src/main/scala/neon/core/VerificationProfile.scala_</small>

A warehouse that requires verification for each-level picks passes
`VerificationProfile(Set(PackagingLevel.Each))`. A warehouse with no
verification requirements passes `VerificationProfile.disabled`.

### Step 0: Validation

```scala
def complete(
    taskId: TaskId,
    actualQuantity: Int,
    verified: Boolean,
    at: Instant
): Either[TaskCompletionError, TaskCompletionResult] =
  if actualQuantity < 0 then
    return Left(TaskCompletionError.InvalidActualQuantity(taskId, actualQuantity))

  taskRepository.findById(taskId) match
    case None                          => Left(TaskCompletionError.TaskNotFound(taskId))
    case Some(assigned: Task.Assigned) =>
      if verificationProfile.requiresVerification(assigned.packagingLevel) && !verified
      then Left(TaskCompletionError.VerificationRequired(taskId))
      else completeAssigned(assigned, actualQuantity, at)
    case Some(_) => Left(TaskCompletionError.TaskNotAssigned(taskId))
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

Before any business logic runs, the service validates inputs and loads the
aggregate. There are four ways to reach the failure track:

1. **Negative quantity.** The `actualQuantity < 0` guard uses an early return.
   Zero is valid (it represents a full shortpick where the worker found nothing
   at the location).
2. **Task not found.** The repository returned `None`.
3. **Wrong state.** The repository found a task, but it is not `Assigned`. The
   typed pattern match `Some(assigned: Task.Assigned)` only matches the one
   valid state. Every other state (Planned, Allocated, Completed, Cancelled)
   falls through to `TaskNotAssigned`.
4. **Verification required.** The task's packaging level requires a
   verification scan, and the caller did not provide one.

Only after passing all four checks does execution continue to `completeAssigned`.

### Step 1: Complete the task

```scala
private def completeAssigned(
    assigned: Task.Assigned,
    actualQuantity: Int,
    at: Instant
): Either[TaskCompletionError, TaskCompletionResult] =
  val (completed, completedEvent) = assigned.complete(actualQuantity, at)
  taskRepository.save(completed, completedEvent)
```

The `assigned.complete(actualQuantity, at)` call is the typestate transition
from Chapter 4. It returns `(Task.Completed, TaskEvent.TaskCompleted)`. The
service destructures the tuple, saves both the new state and the event to the
repository, and continues with the cascade.

### Step 2: Stock consumption

```scala
val stockConsumption = consumeOrDeallocateStock(completed, at)
```

The `consumeOrDeallocateStock` helper handles three scenarios based on what
actually happened during the pick:

```scala
private def consumeOrDeallocateStock(
    completed: Task.Completed,
    at: Instant
): Option[(StockPosition, StockPositionEvent)] =
  (stockPositionRepository, completed.stockPositionId) match
    case (Some(spRepo), Some(spId)) =>
      spRepo.findById(spId).flatMap { sp =>
        val remainder = completed.requestedQuantity - completed.actualQuantity
        if completed.actualQuantity > 0 && remainder > 0 then
          // Partial pick: consume actual, then deallocate remainder
          val (afterConsume, consumeEvent) =
            sp.consumeAllocated(completed.actualQuantity, at)
          spRepo.save(afterConsume, consumeEvent)
          val (afterDeallocate, deallocateEvent) =
            afterConsume.deallocate(remainder, at)
          spRepo.save(afterDeallocate, deallocateEvent)
          Some((afterDeallocate, deallocateEvent))
        else if completed.actualQuantity > 0 then
          // Full pick: consume all
          val (updated, event) =
            sp.consumeAllocated(completed.actualQuantity, at)
          spRepo.save(updated, event)
          Some((updated, event))
        else if completed.requestedQuantity > 0 then
          // Zero pick (full shortpick): deallocate all back to available
          val (updated, event) =
            sp.deallocate(completed.requestedQuantity, at)
          spRepo.save(updated, event)
          Some((updated, event))
        else None
      }
    case _ => None
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

The three branches cover every outcome. **Partial pick** (requested 10,
picked 7): consume 7 from allocated stock, deallocate 3 back to available.
**Full pick** (requested 10, picked 10): consume the full amount. **Zero pick**
(requested 10, picked 0): the worker found nothing, so deallocate everything
back. The outer `match` on `(stockPositionRepository, completed.stockPositionId)`
guards the entire block; if stock management is not configured or the task has
no stock position, the method returns `None` and the cascade continues.

### Step 3: Shortpick detection

```scala
val shortpick = ShortpickPolicy(completed, at)
shortpick.foreach { (replacement, event) => taskRepository.save(replacement, event) }
```

This is the impure/pure/impure sandwich in action. The service calls
`ShortpickPolicy` (a pure function from Chapter 6), which examines the
completed task and decides whether a replacement is needed. If the policy
returns `Some`, the service saves the replacement task. If it returns `None`,
`.foreach` does nothing. The service contains no shortpick logic itself; that
lives entirely in the policy.

### Step 4: Routing

```scala
val routing = RoutingPolicy(completedEvent, at)
routing.foreach { (pending, event) => transportOrderRepository.save(pending, event) }
```

Same pattern, different policy. `RoutingPolicy` checks whether the completed
task had a handling unit and, if so, creates a transport order to move it to
the destination. As we discussed in Chapter 6, the policy takes the _event_
rather than the _state_ because routing is a reaction to something that just
happened.

### Step 5: Wave and picking completion detection

```scala
val (waveCompletion, pickingCompletion) = completed.waveId match
  case None         => (None, None)
  case Some(waveId) =>
    val waveTasks = taskRepository.findByWaveId(waveId)

    val completedWave = waveRepository
      .findById(waveId)
      .collect { case released: Wave.Released =>
        WaveCompletionPolicy(waveTasks, released, at)
      }
      .flatten
    completedWave.foreach { (wave, event) => waveRepository.save(wave, event) }

    val pickedConsolidationGroup = consolidationGroupRepository
      .findByWaveId(waveId)
      .collectFirst {
        case consolidationGroup: ConsolidationGroup.Created
            if consolidationGroup.orderIds.contains(completed.orderId) =>
          consolidationGroup
      }
      .flatMap { consolidationGroup =>
        val consolidationGroupOrderIds = consolidationGroup.orderIds.toSet
        val consolidationGroupTasks =
          waveTasks.filter(t => consolidationGroupOrderIds.contains(t.orderId))
        PickingCompletionPolicy(consolidationGroupTasks, consolidationGroup, at)
      }
    pickedConsolidationGroup.foreach { (consolidationGroup, event) =>
      consolidationGroupRepository.save(consolidationGroup, event)
    }

    (completedWave, pickedConsolidationGroup)
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

This is the most complex part of the cascade, and it deserves a careful reading.

First, the outer `match` on `completed.waveId`. Not every task belongs to a
wave (standalone tasks exist), so if there is no wave, both completion checks
are skipped.

For wave completion, the service loads all tasks for the wave, then loads the
wave itself. The `.collect` filters to `Wave.Released` because only a released
wave can be completed (a planned wave has not started, and a completed or
cancelled wave is already terminal). `WaveCompletionPolicy` checks whether all
tasks have reached a terminal state. If so, it returns the completed wave and
event.

For picking completion, the service looks for the consolidation group that
contains this task's order. The `.collectFirst` finds the first `Created`
consolidation group whose `orderIds` includes the completed task's `orderId`.
Then `PickingCompletionPolicy` checks whether all tasks for that group's orders
are terminal. If they are, the group transitions to `Picked`.

Notice that both policies reuse the `waveTasks` list loaded once at the top of
the block. The service does not query the repository twice. Small detail, but
it matters for both performance and consistency.

### The result

```scala
Right(
  TaskCompletionResult(
    completed = completed,
    completedEvent = completedEvent,
    shortpick = shortpick,
    transportOrder = routing,
    waveCompletion = waveCompletion,
    pickingCompletion = pickingCompletion,
    stockConsumption = stockConsumption
  )
)
```

Everything is bundled into `TaskCompletionResult` and returned as `Right`. One
task completion can produce up to five downstream effects: stock adjustment,
shortpick replacement, transport order, wave completion, and consolidation
group transition. All of them are visible in a single return value.

## Walkthrough: WaveReleaseService

`TaskCompletionService` orchestrates the _end_ of a task's lifecycle.
`WaveReleaseService` orchestrates the _beginning_. When a wave plan is
released, the service creates tasks, optionally allocates stock, and forms
consolidation groups.

```scala
class WaveReleaseService(
    waveRepository: WaveRepository,
    taskRepository: TaskRepository,
    consolidationGroupRepository: ConsolidationGroupRepository,
    stockPositionRepository: Option[StockPositionRepository] = None,
    allocationStrategy: AllocationStrategy = AllocationStrategy.Fifo,
    referenceDate: LocalDate = LocalDate.now()
):
  def release(
      wavePlan: WavePlan,
      at: Instant,
      warehouseAreaId: Option[WarehouseAreaId] = None
  ): WaveReleaseResult =
    waveRepository.save(wavePlan.wave, wavePlan.event)

    val baseTasks = TaskCreationPolicy(wavePlan.taskRequests, at)

    val (tasks, stockAllocations) =
      (stockPositionRepository, warehouseAreaId) match
        case (Some(spRepo), Some(areaId)) =>
          allocateAndEnrich(spRepo, areaId, baseTasks, at)
        case _ => (baseTasks, Nil)

    taskRepository.saveAll(tasks)

    val consolidationGroups =
      ConsolidationGroupFormationPolicy(wavePlan.event, at)
    consolidationGroupRepository.saveAll(consolidationGroups)

    WaveReleaseResult(
      wave = wavePlan.wave,
      event = wavePlan.event,
      tasks = tasks,
      consolidationGroups = consolidationGroups,
      stockAllocations = stockAllocations
    )
```

<small>_File: core/src/main/scala/neon/core/WaveReleaseService.scala_</small>

The five steps mirror the impure/pure/impure sandwich. (1) Persist the
released wave. (2) Create tasks via `TaskCreationPolicy` (Chapter 6). (3) If
both a stock repository and warehouse area are provided, run
`StockAllocationPolicy`, lock stock positions, and enrich tasks with stock
position IDs; otherwise skip. (4) Persist all tasks in batch. (5) Form
consolidation groups via `ConsolidationGroupFormationPolicy` and persist them.

Notice the difference from `TaskCompletionService`. The release service does
not return `Either` because it has no validation to fail. The `WavePlan` is
already validated by the time it reaches the service, so the return type is a
plain `WaveReleaseResult` containing every entity that was created.

## Walkthrough: WaveCancellationService

Where `WaveReleaseService` creates a cascade of new entities,
`WaveCancellationService` tears one down. Cancelling a released wave must also
cancel all of its downstream aggregates: tasks, transport orders, and
consolidation groups.

```scala
sealed trait WaveCancellationError

object WaveCancellationError:
  case class WaveNotFound(waveId: WaveId) extends WaveCancellationError
  case class WaveAlreadyTerminal(waveId: WaveId) extends WaveCancellationError
```

<small>_File: core/src/main/scala/neon/core/WaveCancellationService.scala_</small>

The error ADT is compact: the wave does not exist, or it is already terminal.

```scala
class WaveCancellationService(
    waveRepository: WaveRepository,
    taskRepository: TaskRepository,
    transportOrderRepository: TransportOrderRepository,
    consolidationGroupRepository: ConsolidationGroupRepository
):
  def cancel(
      waveId: WaveId,
      at: Instant
  ): Either[WaveCancellationError, WaveCancellationResult] =
    waveRepository.findById(waveId) match
      case None                          =>
        Left(WaveCancellationError.WaveNotFound(waveId))
      case Some(planned: Wave.Planned)   => cancelPlanned(planned, at)
      case Some(released: Wave.Released) => cancelReleased(released, at)
      case Some(_: Wave.Completed)       =>
        Left(WaveCancellationError.WaveAlreadyTerminal(waveId))
      case Some(_: Wave.Cancelled)       =>
        Left(WaveCancellationError.WaveAlreadyTerminal(waveId))
```

<small>_File: core/src/main/scala/neon/core/WaveCancellationService.scala_</small>

The `cancel` method pattern matches on the wave's current state. A planned wave
has no downstream entities, so cancellation is a single state transition:

```scala
private def cancelPlanned(
    planned: Wave.Planned,
    at: Instant
): Either[WaveCancellationError, WaveCancellationResult] =
  val (cancelled, cancelledEvent) = planned.cancel(at)
  waveRepository.save(cancelled, cancelledEvent)
  Right(WaveCancellationResult(cancelled, cancelledEvent, Nil, Nil, Nil))
```

The three `Nil` values in the result tell the caller that no cascading
cancellations occurred. Clean and explicit.

**Released wave: cancel with cascade.** A released wave has downstream entities
that must also be cancelled:

```scala
private def cancelReleased(
    released: Wave.Released,
    at: Instant
): Either[WaveCancellationError, WaveCancellationResult] =
  val (cancelled, cancelledEvent) = released.cancel(at)
  waveRepository.save(cancelled, cancelledEvent)

  val waveTasks = taskRepository.findByWaveId(released.id)
  val cancelledTasks = TaskCancellationPolicy(waveTasks, at)
  taskRepository.saveAll(cancelledTasks)

  val handlingUnitIds = waveTasks.flatMap(_.handlingUnitId).distinct
  val transportOrders =
    handlingUnitIds.flatMap(transportOrderRepository.findByHandlingUnitId)
  val cancelledTransportOrders =
    TransportOrderCancellationPolicy(transportOrders, at)
  transportOrderRepository.saveAll(cancelledTransportOrders)

  val consolidationGroups =
    consolidationGroupRepository.findByWaveId(released.id)
  val cancelledConsolidationGroups =
    ConsolidationGroupCancellationPolicy(consolidationGroups, at)
  consolidationGroupRepository.saveAll(cancelledConsolidationGroups)

  Right(
    WaveCancellationResult(
      cancelled = cancelled,
      cancelledEvent = cancelledEvent,
      cancelledTasks = cancelledTasks,
      cancelledTransportOrders = cancelledTransportOrders,
      cancelledConsolidationGroups = cancelledConsolidationGroups
    )
  )
```

<small>_File: core/src/main/scala/neon/core/WaveCancellationService.scala_</small>

The cascade follows the dependency graph downward: cancel the wave, then
cancel all non-terminal tasks via `TaskCancellationPolicy`, then find and
cancel associated transport orders via `TransportOrderCancellationPolicy`,
then cancel consolidation groups via `ConsolidationGroupCancellationPolicy`.
Each step follows the same load/decide/save pattern. The policies handle the
filtering (skipping already-terminal entities) so the service does not need to.

## Architecture Note: Railway-Oriented Programming

If you have read Scott Wlaschin's writing on functional error handling, the
service pattern here will look familiar. Wlaschin describes _Railway-Oriented
Programming_ (ROP): treating a computation as a two-track railway where each
step can continue on the success track or divert to the failure track.

In Neon WES, the two tracks are the two sides of `Either`:

- **Right track (success):** the operation produced a result. Continue to the
  next step.
- **Left track (failure):** something went wrong. Skip all remaining steps and
  return the error.

Each step in a service is a "switch function" that can route to the failure
track. The repository lookup returns `None`, and we switch to
`Left(TaskNotFound(...))`. The state pattern match finds a non-Assigned task,
and we switch to `Left(TaskNotAssigned(...))`. Once on the failure track, the
cascade steps never run.

In the async counterpart (which we will see in Chapter 11), `for`-comprehensions
over `Future[Either[...]]` achieve the same two-track flow:

```scala
for
  task      <- findTask(taskId)        // Left if not found
  assigned  <- ensureAssigned(task)    // Left if wrong state
  result    <- completeAssigned(assigned, qty, at)
yield result
```

The shape is the same whether the operations are synchronous or asynchronous.
`Either` provides the railway. Pattern matching and `for`-comprehensions provide
the track switching. No exceptions, no try-catch, no hidden control flow.

## What Comes Next

Services orchestrate policies and repositories, but we have been using
repository traits without seeing their implementations. In the next chapter,
we will explore the dual-interface pattern: sync port traits for fast
in-memory testing and async port traits for production infrastructure backed
by Pekko Cluster Sharding.
