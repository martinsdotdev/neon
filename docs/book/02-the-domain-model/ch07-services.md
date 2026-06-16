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

<small>_File: core/src/main/scala/neon/core/TaskCompletionCascade.scala_</small>

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

<small>_File: core/src/main/scala/neon/core/TaskCompletionCascade.scala_</small>

This result captures _everything_ that happened. The primary action (task
completed) is always present. The cascade effects are each `Option` because
they do not always fire: no shortpick means `shortpick = None`, no handling
unit means `transportOrder = None`, and so on. This gives callers full
visibility. The HTTP layer can choose which fields to include in a response.
The test layer can assert on exactly which cascades fired. Nothing is hidden.

## The Decider Split: Cascade Module and Thin Shells

`TaskCompletionService` is the centerpiece of this chapter, but it is not where
the interesting logic lives. A single completion can trigger up to five
downstream effects: stock adjustment, a shortpick replacement, a transport
order, wave completion, and a consolidation group transition. Coordinating that
cascade is intricate, and the system has both a synchronous and an asynchronous
service that must produce identical results. To keep those two services from
drifting, the cascade is factored into a _pure decision module_ that neither
service can bypass.

That module is `TaskCompletionCascade`. It is a plain `object` with no
repositories, no `Future`, and no I/O of any kind. It exposes two functions:

- `validate` — the gate. Given the loaded task and the inputs, it returns
  `Either[TaskCompletionError, Task.Assigned]`: a `Left` if any precondition
  fails, or a `Right` carrying the one state from which completion is legal.
- `decide` — the cascade. Given a validated `Task.Assigned` and a pre-loaded
  snapshot of the surrounding world, it computes _every_ state transition and
  event the completion produces, plus the ordered list of stock writes.

This is the Decider pattern the book's foreword praised, made concrete:
`validate`/`decide` are pure functions over pre-loaded state, and the services
are thin shells that load, call `decide`, and persist. The shell does the I/O;
the module does the thinking. Because both shells call the same `decide`, the
sync and async paths cannot diverge.

@:callout(info)

`TaskCompletionError` and `TaskCompletionResult` (which we saw above) are both
defined _inside_ `TaskCompletionCascade.scala`, alongside the module. They are
the cascade's vocabulary, not the service's, so they live with the decision
logic that produces them.

@:@

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

### The validation gate

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

The gate takes the loaded task as a parameter; it does not fetch it. There are
four ways to reach the failure track:

1. **Negative quantity.** Zero is valid (it represents a full shortpick where
   the worker found nothing at the location), but a negative quantity is an
   `InvalidActualQuantity`.
2. **Task not found.** The shell passed `None`.
3. **Wrong state.** The typed pattern match `Some(assigned: Task.Assigned)`
   only matches the one valid state. Every other state (Planned, Allocated,
   Completed, Cancelled) falls through to `TaskNotAssigned`.
4. **Verification required.** The task's packaging level requires a
   verification scan, and the caller did not provide one.

On success, `validate` returns the `Task.Assigned` itself, not a bare boolean.
That return type is the contract for `decide`: you cannot call `decide` without
first proving, in the type system, that you hold an assignable task.

### The cascade decision

```scala
def decide(
    assigned: Task.Assigned,
    actualQuantity: Int,
    at: Instant,
    state: CascadeState
): Outcome =
  val (completed, completedEvent) = assigned.complete(actualQuantity, at)
  val stockWrites = stockWritesFor(completed, state.stockPosition, at)
  val shortpick = ShortpickPolicy(completed, at)
  val routing = RoutingPolicy(completedEvent, at)

  val (waveCompletion, pickingCompletion) = completed.waveId match
    case None    => (None, None)
    case Some(_) =>
      // Canonical post-completion task set: represent the completing task by its
      // Completed state whatever the load returned, and include the shortpick
      // replacement so an open remainder suppresses completion in every shell.
      val effectiveWaveTasks =
        state.waveTasks.filterNot(_.id == completed.id) ++
          (completed :: shortpick.map(_._1).toList)

      val waveCompletion = state.wave.collect { case released: Wave.Released =>
        WaveCompletionPolicy(effectiveWaveTasks, released, at)
      }.flatten

      val pickingCompletion = state.consolidationGroups
        .collectFirst {
          case group: ConsolidationGroup.Created
              if group.orderIds.contains(completed.orderId) =>
            group
        }
        .flatMap { group =>
          val groupOrderIds = group.orderIds.toSet
          val groupTasks =
            effectiveWaveTasks.filter(task => groupOrderIds.contains(task.orderId))
          PickingCompletionPolicy(groupTasks, group, at)
        }

      (waveCompletion, pickingCompletion)

  Outcome(
    result = TaskCompletionResult(
      completed = completed,
      completedEvent = completedEvent,
      shortpick = shortpick,
      transportOrder = routing,
      waveCompletion = waveCompletion,
      pickingCompletion = pickingCompletion,
      stockConsumption = stockWrites.lastOption
    ),
    stockWrites = stockWrites
  )
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionCascade.scala_</small>

Read this as five decisions, all over data already in hand:

1. **Complete the task.** `assigned.complete(actualQuantity, at)` is the
   typestate transition from Chapter 4, returning `(Task.Completed, Event)`.
2. **Stock writes.** `stockWritesFor` (below) computes the stock adjustments as
   a pure list. Nothing is persisted here.
3. **Shortpick.** `ShortpickPolicy` decides whether a replacement is needed.
4. **Routing.** `RoutingPolicy` decides whether a transport order is needed.
5. **Wave and picking completion.** Guarded by `completed.waveId`. Standalone
   tasks (no wave) skip both checks.

The wave and picking checks read from `state`: the wave, its tasks, and its
consolidation groups, all loaded by the shell _before_ `decide` ran. But the
loaded `waveTasks` may be stale: it might still show the completing task in its
old `Assigned` state, or miss a just-saved sibling. So `decide` normalizes the
set itself, building `effectiveWaveTasks` by dropping the completing task's old
entry and appending its fresh `Completed` state plus any shortpick replacement.
This is what lets the policy see reality regardless of how stale the shell's
load was, and it is why the shortpick replacement correctly suppresses wave
completion: the open replacement is in the set the policy evaluates.

`decide` returns an `Outcome`, which bundles the `TaskCompletionResult` (the
caller-facing summary) with `stockWrites` (the ordered list the shell must
persist). The `result.stockConsumption` field is simply `stockWrites.lastOption`,
so a caller that only cares about the final stock state can ignore the ordering.

### Stock writes as a pure list

The stock logic that used to live in the service is now a pure function that
returns writes instead of performing them:

```scala
private def stockWritesFor(
    completed: Task.Completed,
    stockPosition: Option[StockPosition],
    at: Instant
): List[(StockPosition, StockPositionEvent)] =
  stockPosition match
    case None                => Nil
    case Some(stockPosition) =>
      val remainder = completed.requestedQuantity - completed.actualQuantity
      if completed.actualQuantity > 0 && remainder > 0 then
        // Partial pick: consume actual, then deallocate remainder
        val (afterConsume, consumeEvent) =
          stockPosition.consumeAllocated(completed.actualQuantity, at)
        val (afterDeallocate, deallocateEvent) = afterConsume.deallocate(remainder, at)
        List((afterConsume, consumeEvent), (afterDeallocate, deallocateEvent))
      else if completed.actualQuantity > 0 then
        // Full pick: consume all
        List(stockPosition.consumeAllocated(completed.actualQuantity, at))
      else if completed.requestedQuantity > 0 then
        // Zero pick (full shortpick): deallocate all back to available
        List(stockPosition.deallocate(completed.requestedQuantity, at))
      else Nil
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionCascade.scala_</small>

The three branches cover every outcome. **Partial pick** (requested 10,
picked 7): consume 7 from allocated stock, then deallocate 3 back to available,
producing _two_ ordered writes. **Full pick** (requested 10, picked 10):
consume the full amount, one write. **Zero pick** (requested 10, picked 0): the
worker found nothing, so deallocate everything back, one write. When the task
has no loaded stock position, the function returns `Nil` and the cascade
continues. Because this is a pure list, the partial-pick ordering (consume
before deallocate) is captured in the data, and the shell can replay it without
re-deriving the logic.

### The synchronous shell

With the decisions extracted, the synchronous service shrinks to a load /
decide / persist shell:

```scala
class TaskCompletionService(
    taskRepository: TaskRepository,
    waveRepository: WaveRepository,
    consolidationGroupRepository: ConsolidationGroupRepository,
    transportOrderRepository: TransportOrderRepository,
    verificationProfile: VerificationProfile,
    stockPositionRepository: Option[StockPositionRepository] = None
):
  def complete(
      taskId: TaskId,
      actualQuantity: Int,
      verified: Boolean,
      at: Instant
  ): Either[TaskCompletionError, TaskCompletionResult] =
    TaskCompletionCascade
      .validate(
        taskId = taskId,
        task = taskRepository.findById(taskId),
        actualQuantity = actualQuantity,
        verified = verified,
        verificationProfile = verificationProfile
      )
      .map { assigned =>
        val outcome = TaskCompletionCascade.decide(
          assigned = assigned,
          actualQuantity = actualQuantity,
          at = at,
          state = loadCascadeState(assigned)
        )
        persist(outcome)
        outcome.result
      }
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

The constructor still takes five repository traits and one configuration
object. Notice that `stockPositionRepository` is `Option`: stock management is
not required for all warehouse configurations, so when the repository is `None`,
`loadCascadeState` simply yields no stock position and the cascade decides
without stock writes.

The `complete` method reads almost like prose: validate the looked-up task; if
that succeeds, load the cascade state, decide, persist the outcome, and hand the
caller the `result`. The two private helpers are mechanical. `loadCascadeState`
fetches everything `decide` needs, and the order matters:

```scala
private def loadCascadeState(assigned: Task.Assigned): CascadeState =
  val stockPosition =
    for
      repository <- stockPositionRepository
      stockPositionId <- assigned.stockPositionId
      position <- repository.findById(stockPositionId)
    yield position
  assigned.waveId match
    case None         => CascadeState.empty.copy(stockPosition = stockPosition)
    case Some(waveId) =>
      CascadeState(
        stockPosition = stockPosition,
        wave = waveRepository.findById(waveId),
        waveTasks = taskRepository.findByWaveId(waveId),
        consolidationGroups = consolidationGroupRepository.findByWaveId(waveId)
      )
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

All loads happen here, before `decide`. The shell never reads its own writes:
once `decide` has run on this snapshot, persistence only writes. That discipline
is what makes the stale-set normalization inside `decide` necessary and
sufficient.

`persist` then writes the outcome in cascade order:

```scala
private def persist(outcome: TaskCompletionCascade.Outcome): Unit =
  taskRepository.save(outcome.result.completed, outcome.result.completedEvent)
  stockPositionRepository.foreach { repository =>
    outcome.stockWrites.foreach { (position, event) => repository.save(position, event) }
  }
  outcome.result.shortpick.foreach { (replacement, event) =>
    taskRepository.save(replacement, event)
  }
  outcome.result.transportOrder.foreach { (pending, event) =>
    transportOrderRepository.save(pending, event)
  }
  outcome.result.waveCompletion.foreach { (wave, event) => waveRepository.save(wave, event) }
  outcome.result.pickingCompletion.foreach { (group, event) =>
    consolidationGroupRepository.save(group, event)
  }
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

Every line is `.save`. There is no logic, no branching beyond "did this effect
fire?", and no business decision. The shell is pure plumbing wrapped around a
pure decision. One task completion can still produce up to five downstream
effects, but each is now a field on the `Outcome` that `persist` mechanically
writes out.

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

For task completion, the single switch is `TaskCompletionCascade.validate`. It
returns `Left` for a missing task, a non-Assigned task, a negative quantity, or
a missing verification scan; it returns `Right(assigned)` otherwise. Once on the
failure track, neither `decide` nor `persist` runs. The shell's `.map` over the
`Either` only fires on the success track.

The async counterpart (which we will see in Chapter 11) achieves the same
two-track flow, but threads the `Future` through it. Because validation needs
the looked-up task, the shell first awaits `findById`, then matches on the
`validate` result, sequencing the loads, the decision, and the persistence in a
`for`-comprehension over `Future` only on the `Right`:

```scala
taskRepository.findById(taskId).flatMap { task =>
  TaskCompletionCascade.validate(taskId, task, actualQuantity, verified, verificationProfile) match
    case Left(error)     => Future.successful(Left(error))
    case Right(assigned) =>
      for
        state   <- loadCascadeState(assigned)  // load wave, tasks, groups, stock
        outcome  = TaskCompletionCascade.decide(assigned, actualQuantity, at, state)
        _       <- persist(outcome)            // fan out the saves
      yield Right(outcome.result)
}
```

<small>_File: core/src/main/scala/neon/core/AsyncTaskCompletionService.scala_</small>

The decision in the middle is the very same `TaskCompletionCascade.decide` the
synchronous shell calls; only the load and persist steps are wrapped in `Future`.
That shared core is the whole point of extracting the cascade: the two shells
cannot drift, because the business logic lives in neither of them. The async
shell's constructor differs in one telling way from the sync one. It takes a
required `stockPositionRepository: AsyncStockPositionRepository` rather than an
`Option`, closing a gap where the async path used to skip stock consumption
entirely. `decide` produces the stock writes for both shells identically; the
async `persist` folds them sequentially, because the deallocate of a partial
pick must reach the actor only after the consume that precedes it.

`Either` provides the railway. Pattern matching and `for`-comprehensions provide
the track switching. No exceptions, no try-catch, no hidden control flow.

## What Comes Next

Services orchestrate policies and repositories, but we have been using
repository traits without seeing their implementations. In the next chapter,
we will explore the dual-interface pattern: sync port traits for fast
in-memory testing and async port traits for production infrastructure backed
by Pekko Cluster Sharding.
