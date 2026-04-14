# Policies: Pure Business Logic

When a worker picks 7 items instead of the requested 10, who decides that a
replacement task should be created for the remaining 3? When all tasks for a
wave are finished, who detects that the wave itself is complete? The answer,
in both cases, is a *policy*.

In Chapter 4 we learned how typestate encoding makes illegal transitions
unrepresentable. In Chapter 5 we saw how events record every state change as
an immutable fact. But neither typestates nor events answer a critical
question: *given some facts about the world, what should happen next?* That
is the job of a policy. In this chapter, we will build the decision-making
layer of Neon WES, one pure function at a time.


## What Is a Policy?

A policy is a stateless decision object. It takes domain values in and returns
a decision out. No dependencies, no side effects, no repositories, no clocks.
A policy is a pure function wrapped in a Scala `object`.

This is a deliberate design choice, recorded in ADR-0002. The core module uses
a three-layer pattern:

- **Policies** decide *what* should happen. Pure business rules, no I/O.
- **Services** orchestrate *how* it happens. They read from repositories, call
  policies, and write results back.
- **Repositories** define *where* data lives. Abstract port traits with no
  concrete implementation in the domain layer.

Policies sit at the bottom of this stack. They depend on nothing. Services call
them, but policies never call services, never call repositories, never reach
out to the external world. This constraint is what makes them so easy to test
and so easy to reason about.

@:callout(info)

The Policy-Service-Repository pattern is documented in
ADR-0002.
The short version: separate stateless decision objects (policies) from
orchestration (services) and persistence (repositories) so that business
rules are trivially testable and reusable across services.

@:@


## Anatomy of a Policy

Let's start with the canonical example. When a pick task completes with fewer
items than requested (a *shortpick*), the system needs to decide whether to
create a replacement task for the remainder. Here is the policy that makes
that decision:

```scala
package neon.core

import neon.task.{Task, TaskEvent}

import java.time.Instant

object ShortpickPolicy:

  def apply(
      completed: Task.Completed,
      at: Instant
  ): Option[(Task.Planned, TaskEvent.TaskCreated)] =
    val remainder = completed.requestedQuantity - completed.actualQuantity
    if remainder <= 0 then None
    else
      Some(
        Task.create(
          taskType = completed.taskType,
          skuId = completed.skuId,
          packagingLevel = completed.packagingLevel,
          requestedQuantity = remainder,
          orderId = completed.orderId,
          waveId = completed.waveId,
          parentTaskId = Some(completed.id),
          handlingUnitId = completed.handlingUnitId,
          at = at
        )
      )
```

<small>*File: core/src/main/scala/neon/core/ShortpickPolicy.scala*</small>

Let's walk through this line by line.

### `object`: No state, no dependencies

The policy is defined as a Scala `object`, not a `class`. There is no
constructor, no injected dependencies, no mutable fields. You cannot
instantiate it with different configurations. There is exactly one
`ShortpickPolicy`, and it behaves the same way every time it is called.

### The `apply` signature: domain values in, decision out

The method takes two arguments: the completed task and a timestamp. Both are
plain domain values. It returns `Option[(Task.Planned, TaskEvent.TaskCreated)]`.
The `Option` encodes the decision itself: `Some` means "yes, create a
replacement task," and `None` means "no action needed." The tuple inside the
`Some` follows the same `(NewState, Event)` pattern we saw in Chapter 4 for
typestate transitions.

Notice what the signature does *not* include: no `Future`, no `Either` wrapping
an I/O error, no repository to look up additional data. Everything the policy
needs to make its decision arrives through the parameters.

### The logic: arithmetic and a conditional

The core logic is three lines. Calculate the remainder. If it is zero or
negative, return `None`. Otherwise, create a replacement task via `Task.create`
(the same factory method from Chapter 4) and return `Some`.

The `Task.create` call passes `parentTaskId = Some(completed.id)`. This is a
small but important detail: the replacement task knows which original task
spawned it. This parent-child link enables traceability. When an operator or
supervisor wants to understand why a particular task exists, they can follow
the chain back to the original shortpick.


## Testing Policies

Because a policy is a pure function with no dependencies, testing it requires
no mocks, no test containers, no setup infrastructure. You construct the input,
call the function, and assert on the output. Here is the test suite for
`ShortpickPolicy`:

```scala
class ShortpickPolicySuite extends AnyFunSpec with OptionValues:
  val skuId = SkuId()
  val userId = UserId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val at = Instant.now()

  def completed(
      requested: Int,
      actual: Int,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId),
      packagingLevel: PackagingLevel = PackagingLevel.Each
  ) =
    Task.Completed(
      TaskId(), TaskType.Pick, skuId, packagingLevel,
      requested, actual, orderId, waveId, None,
      handlingUnitId, None, sourceLocationId,
      destinationLocationId, userId
    )

  describe("ShortpickPolicy"):
    describe("when actual meets requested"):
      it("does not create a replacement task"):
        assert(ShortpickPolicy(completed(10, 10), at).isEmpty)

    describe("when actual exceeds requested"):
      it("does not create a replacement task"):
        assert(ShortpickPolicy(completed(10, 12), at).isEmpty)

    describe("when actual is zero"):
      it("returns replacement task with full requested quantity"):
        val (replacement, _) = ShortpickPolicy(completed(10, 0), at).value
        assert(replacement.requestedQuantity == 10)

    describe("when actual is less than requested"):
      val task = completed(10, 7)
      val (replacement, event) = ShortpickPolicy(task, at).value

      it("returns replacement task for the unfulfilled quantity"):
        assert(replacement.requestedQuantity == 3)

      it("copies wave, SKU, task type, and order ID from original"):
        assert(replacement.waveId == task.waveId)
        assert(replacement.skuId == task.skuId)
        assert(replacement.taskType == task.taskType)
        assert(replacement.orderId == task.orderId)

      it("sets parentTaskId to the original task's ID"):
        assert(replacement.parentTaskId.value == task.id)
```

<small>*File: core/src/test/scala/neon/core/ShortpickPolicySuite.scala*</small>

A few things to notice about this test style.

**No mocks.** The test constructs a `Task.Completed` directly, calls the
policy, and inspects the result. There is no mock framework, no stub
repository, no dependency injection container. Pure functions yield pure tests.

**Factory methods for test data.** The `completed(requested, actual)` helper
creates a `Task.Completed` with sensible defaults, letting each test focus on
the two numbers that matter. Optional parameters like `packagingLevel` allow
specific tests to override defaults when they need to verify propagation of
a particular field.

**Edge cases are obvious.** Full pick (10 of 10): no replacement. Over-pick
(12 of 10): no replacement. Zero pick (0 of 10): full replacement. Partial
pick (7 of 10): replacement for 3. The `Option` return type makes each case
read naturally. `isEmpty` for "nothing happened," `.value` (from
`OptionValues`) for "something happened, let me inspect it."

**The `(State, Event)` tuple is destructured inline.** When we write
`val (replacement, event) = ShortpickPolicy(task, at).value`, we get both
pieces of the decision in a single call, ready to assert on separately.

These tests run in milliseconds. There is no database to start, no actor
system to boot, no network to configure. This is the payoff of the policy
pattern: the most important business rules in the system are the cheapest to
test.


## The Policy Catalogue

Neon WES ships with a collection of policies that cover the major decision
points in warehouse execution. Let's walk through each one, grouped by the
kind of decision it makes.

### Task Lifecycle Policies

**TaskCreationPolicy** converts task requests (produced when a wave is
released) into planned tasks:

```scala
object TaskCreationPolicy:

  def apply(
      taskRequests: List[TaskRequest],
      at: Instant
  ): List[(Task.Planned, TaskEvent.TaskCreated)] =
    taskRequests.map: req =>
      Task.create(
        taskType = TaskType.Pick,
        skuId = req.skuId,
        packagingLevel = req.packagingLevel,
        requestedQuantity = req.quantity,
        orderId = req.orderId,
        waveId = Some(req.waveId),
        parentTaskId = None,
        handlingUnitId = None,
        at = at
      )
```

<small>*File: core/src/main/scala/neon/core/TaskCreationPolicy.scala*</small>

This is the simplest kind of policy: a straight mapping. Each `TaskRequest`
becomes exactly one `Task.Planned`. There is no filtering, no conditional
logic, no `Option` in the return type. The list goes in, a list of equal
length comes out. Notice that `parentTaskId` is `None` because these are
original tasks, not shortpick replacements.

**ShortpickPolicy** (which we already examined in detail) handles the other end
of the task lifecycle: creating replacement tasks when picks come up short.

Together, these two policies govern every task creation in the system.
`TaskCreationPolicy` creates the initial batch from wave release.
`ShortpickPolicy` creates follow-up tasks when things do not go as planned.

### Completion Detection Policies

Two policies share the same structural pattern: check whether all tasks in a
group have reached a terminal state, and if so, transition the parent entity.

**WaveCompletionPolicy** checks whether a wave is done:

```scala
object WaveCompletionPolicy:
  import TaskPredicates.isTerminal

  def apply(
      waveTasks: List[Task],
      wave: Wave.Released,
      at: Instant
  ): Option[(Wave.Completed, WaveEvent.WaveCompleted)] =
    if waveTasks.isEmpty then None
    else if waveTasks.forall(isTerminal) then Some(wave.complete(at))
    else None
```

<small>*File: core/src/main/scala/neon/core/WaveCompletionPolicy.scala*</small>

**PickingCompletionPolicy** does the same for consolidation groups:

```scala
object PickingCompletionPolicy:
  import TaskPredicates.isTerminal

  def apply(
      tasks: List[Task],
      consolidationGroup: ConsolidationGroup.Created,
      at: Instant
  ): Option[(ConsolidationGroup.Picked, ConsolidationGroupEvent.ConsolidationGroupPicked)] =
    if tasks.isEmpty then None
    else if tasks.forall(isTerminal) then Some(consolidationGroup.pick(at))
    else None
```

<small>*File: core/src/main/scala/neon/core/PickingCompletionPolicy.scala*</small>

Both policies rely on `TaskPredicates.isTerminal`, a shared helper:

```scala
private[core] object TaskPredicates:

  def isTerminal(task: Task): Boolean = task match
    case _: Task.Completed => true
    case _: Task.Cancelled => true
    case _                 => false
```

<small>*File: core/src/main/scala/neon/core/TaskPredicates.scala*</small>

The predicate uses pattern matching on the typestate-encoded `Task` trait. A
task is terminal if it is `Completed` or `Cancelled`. All other states
(Planned, Allocated, Assigned) are non-terminal. This is the typestate pattern
from Chapter 4 paying dividends: the pattern match is exhaustive over the
sealed trait, so adding a new task state would force us to update this
predicate.

Notice the structural similarity between `WaveCompletionPolicy` and
`PickingCompletionPolicy`. Both take a list of tasks and a parent entity, both
check `forall(isTerminal)`, both return `Option[(NewState, Event)]`. The only
differences are the parent type (`Wave.Released` vs. `ConsolidationGroup.Created`)
and the transition method (`complete` vs. `pick`). This consistency is not
accidental. When every policy follows the same shape, reading a new policy
takes seconds, not minutes.

Both policies also guard against empty task lists. An empty wave or an empty
consolidation group should not be considered "complete" just because
`List.empty.forall(...)` returns `true` (which it does, vacuously). The
explicit `isEmpty` check prevents this subtle bug.

### Routing

**RoutingPolicy** creates a transport order when a task completes with an
associated handling unit:

```scala
object RoutingPolicy:

  def apply(
      event: TaskEvent.TaskCompleted,
      at: Instant
  ): Option[(TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)] =
    event.handlingUnitId.map { handlingUnitId =>
      TransportOrder.create(handlingUnitId, event.destinationLocationId, at)
    }
```

<small>*File: core/src/main/scala/neon/core/RoutingPolicy.scala*</small>

This policy is a single expression. The `Option` comes directly from
`event.handlingUnitId`: if the completed task had a handling unit, create a
transport order to move it to the destination; if not, do nothing. Not every
task involves a handling unit (some task types like replenishment or transfer
may operate without one), so the `Option` handles both cases elegantly.

The policy takes a `TaskEvent.TaskCompleted` rather than a `Task.Completed`.
This is a deliberate choice. The routing decision fires in response to an
event (something that just happened), not in response to the current state of
the task. The event carries exactly the fields the policy needs:
`handlingUnitId` and `destinationLocationId`.

### Formation

**ConsolidationGroupFormationPolicy** creates consolidation groups when a wave
is released, but only for multi-order waves:

```scala
object ConsolidationGroupFormationPolicy:

  def apply(
      event: WaveEvent.WaveReleased,
      at: Instant
  ): List[(ConsolidationGroup.Created, ConsolidationGroupEvent.ConsolidationGroupCreated)] =
    event.orderGrouping match
      case OrderGrouping.Multi =>
        List(ConsolidationGroup.create(event.waveId, event.orderIds, at))
      case OrderGrouping.Single => Nil
```

<small>*File: core/src/main/scala/neon/core/ConsolidationGroupFormationPolicy.scala*</small>

The `OrderGrouping` enum (from Chapter 3) drives the decision. A `Multi` wave
groups multiple orders together for consolidation at a workstation, so the
policy creates a consolidation group. A `Single` wave processes each order
independently, so no groups are needed and the policy returns `Nil`.

The return type here is `List` rather than `Option`. This makes sense because
future extensions might create multiple consolidation groups per wave (for
example, splitting a large wave into groups of manageable size). The `List`
return type anticipates that possibility without requiring a signature change.


## Deep Dive: StockAllocationPolicy

The policies we have seen so far are compact. Each one makes a single decision
in a few lines. `StockAllocationPolicy` is different. It is the most complex
policy in the system, and it deserves a closer look.

The problem it solves: given a list of SKU/quantity requests and a map of
available stock positions, decide which stock to allocate. The allocation must
respect inventory status, shelf life constraints, and a configurable sorting
strategy.

### The input and output types

```scala
case class AllocationRequest(skuId: SkuId, quantity: Int)

case class StockAllocation(
    stockPositionId: StockPositionId,
    quantity: Int,
    lotAttributes: LotAttributes
)

case class AllocationResult(
    request: AllocationRequest,
    allocations: List[StockAllocation],
    shortQuantity: Int
)
```

<small>*File: core/src/main/scala/neon/core/StockAllocationPolicy.scala*</small>

An `AllocationRequest` says "I need this many units of this SKU." A
`StockAllocation` says "take this many from this stock position, with these
lot attributes." An `AllocationResult` ties them together, and includes a
`shortQuantity` field for the unfulfilled remainder. If the warehouse has 7
units available but 10 are requested, `shortQuantity` is 3.

### The signature

```scala
object StockAllocationPolicy:

  def apply(
      requests: List[AllocationRequest],
      availableStock: Map[SkuId, List[StockPosition]],
      strategy: AllocationStrategy,
      referenceDate: LocalDate,
      minimumShelfLifeDays: Int = 0
  ): Either[StockAllocationError, List[AllocationResult]] =
```

<small>*File: core/src/main/scala/neon/core/StockAllocationPolicy.scala*</small>

This is the first policy we have seen that returns `Either` instead of
`Option`. The reason: allocation can fail in ways that need to be
distinguished. Running out of stock and failing a shelf life check are
different errors that require different responses. The `Option` policies
express a binary "do something or do nothing" decision. This policy expresses
a three-way outcome: success, insufficient stock, or insufficient shelf life.

```scala
sealed trait StockAllocationError

object StockAllocationError:
  case class InsufficientStock(skuId: SkuId, requested: Int, available: Int)
      extends StockAllocationError
  case class InsufficientShelfLife(skuId: SkuId, requiredDays: Int, availableDays: Int)
      extends StockAllocationError
```

<small>*File: core/src/main/scala/neon/core/StockAllocationError.scala*</small>

Even the error type is a sealed trait with descriptive case classes. No
exception strings, no error codes. The caller can pattern match and handle each
failure precisely.

### The algorithm

The allocation works in four stages:

1. **Filter by inventory status.** Only positions with `InventoryStatus.Available`
   and a positive available quantity are eligible. Blocked, damaged, or
   quality-hold stock is excluded.

2. **Filter by shelf life.** If `minimumShelfLifeDays` is greater than zero,
   positions whose remaining shelf life falls below the threshold are removed.
   If this filter eliminates all candidates, the policy returns
   `InsufficientShelfLife` with the best available shelf life for diagnostic
   purposes.

3. **Sort by strategy.** The `AllocationStrategy` enum determines the sort
   order:
   - `Fefo` (First Expired, First Out): sort by expiration date ascending,
     then production date, then quantity. Items closest to expiration get
     picked first.
   - `Fifo` (First In, First Out): sort by production date ascending, then
     quantity. The oldest stock gets picked first.
   - `NearestLocation`: reserved for future implementation when location
     proximity scoring is added.

4. **Greedy first-fit allocation.** Walk the sorted list, taking as much as
   possible from each position until the requested quantity is fulfilled or all
   eligible stock is exhausted. Any unfulfilled remainder is recorded in
   `shortQuantity`.

```scala
private def sortByStrategy(
    positions: List[StockPosition],
    strategy: AllocationStrategy,
    referenceDate: LocalDate
): List[StockPosition] =
  strategy match
    case AllocationStrategy.Fefo =>
      positions.sortBy(sp =>
        (
          sp.lotAttributes.expirationDate.getOrElse(LocalDate.MAX),
          sp.lotAttributes.productionDate.getOrElse(LocalDate.MAX),
          sp.availableQuantity
        )
      )
    case AllocationStrategy.Fifo =>
      positions.sortBy(sp =>
        (
          sp.lotAttributes.productionDate.getOrElse(LocalDate.MAX),
          sp.availableQuantity
        )
      )
    case AllocationStrategy.NearestLocation =>
      positions
```

<small>*File: core/src/main/scala/neon/core/StockAllocationPolicy.scala*</small>

The `sortBy` calls use tuple ordering to break ties. For FEFO, positions with
no expiration date sort last (`LocalDate.MAX`), which is the safe default: if
we do not know when something expires, pick the stock we *do* know about
first. The same logic applies to missing production dates in FIFO.

```scala
private def greedyAllocate(
    sorted: List[StockPosition],
    needed: Int
): (List[StockAllocation], Int) =
  var remaining = needed
  val allocations = scala.collection.mutable.ListBuffer[StockAllocation]()
  for sp <- sorted if remaining > 0 do
    val qty = math.min(remaining, sp.availableQuantity)
    allocations += StockAllocation(sp.id, qty, sp.lotAttributes)
    remaining -= qty
  (allocations.toList, remaining)
```

<small>*File: core/src/main/scala/neon/core/StockAllocationPolicy.scala*</small>

The greedy allocator is the only place in the entire policy that uses mutable
state (a `var` and a `ListBuffer`). This is a pragmatic choice: the mutable
loop is easier to read than a fold for this particular algorithm, and the
mutation is entirely local. The `ListBuffer` is converted to an immutable
`List` before it leaves the method. No caller ever sees the mutable
internals.

### Partial allocation support

An important design decision: the policy does not fail when it cannot fully
satisfy a request. If 10 units are requested but only 7 are available, the
result contains allocations for 7 with `shortQuantity = 3`. The calling
service decides how to handle the short: it might proceed with a partial pick,
create a replenishment request, or reject the wave entirely. The policy
reports the facts; the service makes the judgment call.

This is the policy pattern working as intended. The complex stock selection
logic is isolated in a pure function. The orchestration decision ("what do we
do about a short?") belongs in the service layer, which has access to
configuration, business rules, and the broader context.

@:callout(info)

`StockAllocationPolicy` is the most algorithmically complex policy
in Neon WES, but it follows the same structural rules as every other policy:
it is a stateless `object`, it takes domain values as input, and it returns
a result with no side effects. Complexity in the algorithm does not require
complexity in the architecture.

@:@


## Architecture Note: Functional Core, Imperative Shell

If the policy pattern feels familiar, you may be thinking of Gary Bernhardt's
*Functional Core, Imperative Shell* architecture. The idea is to separate your
system into two layers:

- The **functional core** contains pure logic with no dependencies. You can
  test it with simple inputs and outputs.
- The **imperative shell** handles I/O, state management, and coordination.
  It calls into the functional core, but the core never calls back.

In Neon WES, the functional core has two components:

1. **Aggregate transition methods** (Chapter 4). A `Task.Planned` has an
   `allocate()` method that returns `(Task.Allocated, TaskEvent.TaskAllocated)`.
   Pure data transformation, no side effects.

2. **Policies** (this chapter). `ShortpickPolicy` takes a completed task and
   returns an optional replacement. `WaveCompletionPolicy` takes a list of
   tasks and a wave and returns an optional completion. Pure decisions, no
   side effects.

The imperative shell is the service layer (Chapter 7). A service reads tasks
from a repository, passes them to `WaveCompletionPolicy`, and if the policy
returns `Some`, saves the completed wave back to the repository. The service
does the I/O. The policy does the thinking.

This separation explains why policy tests are so fast. They do not need to
start a database, boot an actor system, or configure a network. They call a
function and check the result. The most critical business rules in your system
should also be the cheapest to verify, and the functional core, imperative
shell pattern makes that possible.

The aggregate transition methods and the policies together form a layer of
pure, deterministic logic at the heart of the application. Everything else
(services, repositories, actors, HTTP routes, projections) exists to feed data
into this core and to act on its decisions.


## What Comes Next

Policies make decisions, but they do not act on them. They cannot read from a
database or save a result. For that, we need services. In the next chapter, we
will see how services orchestrate policies with repository access to implement
complex multi-step business operations, like completing a task that triggers a
shortpick check, a routing decision, a wave completion check, and a
consolidation group transition, all in a single coordinated flow.
