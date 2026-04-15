# Project: Building the Wave Release Flow

This is our first project chapter. Instead of introducing new concepts, we will
bring together everything from Part II to trace a complete warehouse workflow:
from customer orders to task completion and wave closure. We will follow the
data through six layers of the system.

Here is what we are applying:

1. **Opaque type IDs** (Chapter 3): `WaveId`, `TaskId`, `OrderId`, `SkuId`,
   and friends give us compile-time safety across aggregate boundaries.
2. **Enums** (Chapter 3): `OrderGrouping`, `PackagingLevel`, and `Priority`
   constrain domain values to valid choices.
3. **Typestate transitions** (Chapter 4): `Wave.Planned.release`,
   `Task.Planned.allocate`, `Task.Allocated.assign`, `Task.Assigned.complete`,
   and `Wave.Released.complete` enforce legal lifecycle progressions at compile
   time.
4. **Events** (Chapter 5): `WaveReleased`, `TaskCreated`, `TaskCompleted`, and
   `WaveCompleted` record every state change as an immutable fact.
5. **Policies** (Chapter 6): `TaskCreationPolicy`, `ShortpickPolicy`,
   `RoutingPolicy`, `WaveCompletionPolicy`, and
   `ConsolidationGroupFormationPolicy` make pure decisions with no side effects.
6. **Services** (Chapter 7): `WaveReleaseService` and `TaskCompletionService`
   orchestrate the cascade, calling policies and persisting results through
   repositories.
7. **Repositories** (Chapter 8): `WaveRepository`, `TaskRepository`,
   `ConsolidationGroupRepository`, and `TransportOrderRepository` define
   abstract persistence ports that we fill with in-memory adapters for testing.

By the end of this chapter, we will have planned a wave, released it, created
tasks, completed picks (including a shortpick), and watched the wave close
itself. Every line of code is real. Every transition follows the rules we
built in the preceding six chapters.

## The Scenario

Let's set the scene in concrete warehouse terms.

Two customer orders arrive. Order 1 requests 10 units of SKU-A, packed as
eaches. Order 2 requests 5 units of SKU-B, also eaches. The warehouse
manager groups these orders into a single wave with `OrderGrouping.Single`,
meaning each order is fulfilled independently with no consolidation.

The goal: release the wave, create one pick task per order line, complete
both picks, and watch the wave close itself when all tasks reach a terminal
state. Along the way, one picker will shortpick (pick fewer items than
requested), triggering a replacement task. We will complete that replacement
too, finally closing the wave.

Here is the plan, step by step:

1. **Plan the wave**: `WavePlanner.plan` creates a `Wave.Released` and two
   `TaskRequest` values.
2. **Release the wave**: `WaveReleaseService.release` persists the wave,
   creates `Task.Planned` instances via `TaskCreationPolicy`, and checks for
   consolidation groups.
3. **Allocate and assign tasks**: Typestate transitions move each task from
   `Planned` to `Allocated` to `Assigned`.
4. **Complete task 1 (full pick)**: `TaskCompletionService.complete` fires the
   cascade. `ShortpickPolicy` finds no shortage. `WaveCompletionPolicy` sees
   open tasks remaining.
5. **Complete task 2 (shortpick)**: The picker gets 3 of 5 units.
   `ShortpickPolicy` creates a replacement task for the remaining 2.
   `WaveCompletionPolicy` still sees an open task.
6. **Complete the replacement task**: Now every task is terminal.
   `WaveCompletionPolicy` fires and transitions the wave to `Completed`.

## Setting the Stage

Before we can trace the flow, we need test data and repositories. This is the
hexagonal architecture from Chapter 8 in action: we swap production
persistence adapters for in-memory implementations backed by mutable maps and
list buffers.

```scala
val skuA = SkuId()
val skuB = SkuId()
val orderId1 = OrderId()
val orderId2 = OrderId()
val at = Instant.now()

val order1 = Order(
  orderId1, Priority.Normal,
  List(OrderLine(skuA, PackagingLevel.Each, 10))
)
val order2 = Order(
  orderId2, Priority.Normal,
  List(OrderLine(skuB, PackagingLevel.Each, 5))
)
```

Each `SkuId()` and `OrderId()` call generates a fresh UUID v7 (Chapter 3). The
`Order` case class holds an id, a priority, and a list of order lines. Each
`OrderLine` pins down one SKU, a packaging level, and a quantity.

Now the repositories:

```scala
val waveRepo = InMemoryWaveRepository()
val taskRepo = InMemoryTaskRepository()
val cgRepo   = InMemoryConsolidationGroupRepository()
val toRepo   = InMemoryTransportOrderRepository()
```

These are the same in-memory implementations from our test suites. Let's look
at one to recall the pattern:

```scala
class InMemoryWaveRepository extends WaveRepository:
  val store: mutable.Map[WaveId, Wave] = mutable.Map.empty
  val events: mutable.ListBuffer[WaveEvent] = mutable.ListBuffer.empty
  def findById(id: WaveId): Option[Wave] = store.get(id)
  def save(wave: Wave, event: WaveEvent): Unit =
    store(wave.id) = wave
    events += event
```

<small>_File: core/src/test/scala/neon/core/WaveReleaseServiceSuite.scala_</small>

A `mutable.Map` for state, a `mutable.ListBuffer` for events. The `save`
method updates the map and appends the event. The `findById` method looks up
by id, returning `Option[Wave]`. No database, no actor, no network. This is
the power of the port trait pattern: the domain code calls `waveRepo.save()`
and has no idea whether it is talking to PostgreSQL or a hash map.

Finally, we wire up the services:

```scala
val releaseService = WaveReleaseService(
  waveRepo, taskRepo, cgRepo
)

val completionService = TaskCompletionService(
  taskRepo, waveRepo, cgRepo, toRepo,
  VerificationProfile.disabled
)
```

`VerificationProfile.disabled` tells the completion service not to require
barcode verification for any packaging level. In production, you would
configure this per warehouse. For our walkthrough, we keep it simple.

The stage is set. Two orders, four repositories, two services. Let's go.

## Step 1: Planning the Wave

Everything begins with `WavePlanner.plan`:

```scala
object WavePlanner:
  def plan(
      orders: List[Order],
      grouping: OrderGrouping,
      at: Instant,
      lineResolution: (WaveId, OrderId, OrderLine) => List[TaskRequest]
        = passThrough
  ): WavePlan =
    require(orders.nonEmpty, "orders must not be empty")
    val id = WaveId()
    val orderIds = orders.map(_.id)
    val planned = Wave.Planned(id, grouping, orderIds)
    val (released, event) = planned.release(at)

    val taskRequests = for
      order <- orders
      line <- order.lines
      req <- lineResolution(id, order.id, line)
    yield req

    WavePlan(released, event, taskRequests)
```

<small>_File: wave/src/main/scala/neon/wave/WavePlanner.scala_</small>

Let's trace this line by line with our data.

**Precondition check.** `require(orders.nonEmpty)` guards against an empty
order list. We have two orders, so we pass. This is the `require()`
convention from Chapter 4: validate preconditions on aggregate creation.

**Fresh ID.** `WaveId()` generates a UUID v7. This is the opaque type pattern
from Chapter 3. The wave gets a unique identifier that cannot be confused
with any other ID type.

**Build Planned state.** `Wave.Planned(id, grouping, orderIds)` creates the
initial typestate. At this point the wave is `Planned`, and only the `release`
and `cancel` methods are available. You cannot call `complete` on a `Planned`
wave. The compiler will not allow it.

**Typestate transition.** `planned.release(at)` returns a tuple:
`(Wave.Released, WaveEvent.WaveReleased)`. The wave jumps from `Planned` to
`Released` in a single call. After this line, the `planned` value is
discarded. Only `released` continues forward.

**Expand order lines.** The `for` comprehension iterates over each order, then
each line within the order, then calls `lineResolution` to produce task
requests. The default `passThrough` strategy maps each order line to exactly
one `TaskRequest`:

```scala
private val passThrough: (WaveId, OrderId, OrderLine) => List[TaskRequest] =
  (waveId, orderId, line) =>
    List(TaskRequest(waveId, orderId, line.skuId, line.packagingLevel, line.quantity))
```

With two orders and one line each, we get two task requests.

Let's call it:

```scala
val wavePlan = WavePlanner.plan(
  List(order1, order2), OrderGrouping.Single, at
)
```

After this call, `wavePlan` contains:

- `wavePlan.wave`: a `Wave.Released` with a fresh `WaveId`, `Single` grouping,
  and both order IDs
- `wavePlan.event`: a `WaveEvent.WaveReleased` recording the release instant
- `wavePlan.taskRequests`: two `TaskRequest` values, one for 10 units of
  SKU-A, one for 5 units of SKU-B

@:callout(info)

`WavePlanner` combines planning and releasing into a single step.
The `Planned` state exists for scenarios where a wave needs approval before
release. In the fast path, it is created and released in the same call.

@:@

## Step 2: Releasing the Wave

The wave plan is a pure data structure. Nothing has been persisted yet. That is
the job of `WaveReleaseService.release`:

```scala
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

  WaveReleaseResult(wavePlan.wave, wavePlan.event,
    tasks, consolidationGroups, stockAllocations)
```

<small>_File: core/src/main/scala/neon/core/WaveReleaseService.scala_</small>

Five things happen in sequence. Let's trace each one.

**1. Persist the released wave.** `waveRepository.save(wavePlan.wave,
wavePlan.event)` writes the `Wave.Released` and its `WaveReleased` event into
the repository. In our in-memory setup, this means the wave lands in
`waveRepo.store` and the event appends to `waveRepo.events`.

**2. Create tasks via TaskCreationPolicy.** This is the first policy call:

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

<small>_File: core/src/main/scala/neon/core/TaskCreationPolicy.scala_</small>

Pure function. No repositories, no side effects. Each `TaskRequest` becomes a
`Task.Planned` paired with a `TaskEvent.TaskCreated`. The task type is always
`Pick` for wave-originated tasks. `parentTaskId` is `None` because these are
original tasks, not shortpick replacements. The `waveId` links each task back
to its wave for completion detection later.

With our two task requests, this produces two `(Task.Planned,
TaskEvent.TaskCreated)` pairs.

**3. Stock allocation (skipped).** We constructed our service without a
`StockPositionRepository`, so the pattern match falls through to `(baseTasks,
Nil)`. In a production scenario with stock tracking enabled, the service would
call `StockAllocationPolicy` to reserve inventory before tasks go to the floor.

**4. Persist all tasks.** `taskRepository.saveAll(tasks)` writes both planned
tasks and their creation events.

**5. Form consolidation groups.** The last policy call:

```scala
object ConsolidationGroupFormationPolicy:
  def apply(
      event: WaveEvent.WaveReleased,
      at: Instant
  ): List[(ConsolidationGroup.Created,
           ConsolidationGroupEvent.ConsolidationGroupCreated)] =
    event.orderGrouping match
      case OrderGrouping.Multi =>
        List(ConsolidationGroup.create(event.waveId, event.orderIds, at))
      case OrderGrouping.Single => Nil
```

<small>_File: core/src/main/scala/neon/core/ConsolidationGroupFormationPolicy.scala_</small>

Our wave uses `OrderGrouping.Single`, so this returns `Nil`. No consolidation
groups are created. If we had used `Multi` grouping, the policy would create a
consolidation group linking all orders in the wave.

Let's call the service:

```scala
val releaseResult = releaseService.release(wavePlan, at)
```

After this call, the system state is:

| Entity               | Count | State    |
| -------------------- | ----- | -------- |
| Wave                 | 1     | Released |
| Tasks                | 2     | Planned  |
| Consolidation Groups | 0     | n/a      |

Two planned tasks, one released wave, zero consolidation groups. The wave
is in motion.

## Step 3: Task Allocation and Assignment

The release service leaves tasks in the `Planned` state. Before a picker can
work a task, it must pass through two more typestate transitions: allocation
(assigning source and destination locations) and assignment (assigning a user).

```scala
val sourceLocation = LocationId()
val destinationLocation = LocationId()
val pickerId = UserId()
```

Let's take the first task through both transitions:

```scala
val task1Planned = taskRepo.store.values
  .find(_.orderId == orderId1)
  .get.asInstanceOf[Task.Planned]

val (task1Allocated, allocatedEvent) =
  task1Planned.allocate(sourceLocation, destinationLocation, at)
taskRepo.save(task1Allocated, allocatedEvent)

val (task1Assigned, assignedEvent) =
  task1Allocated.assign(pickerId, at)
taskRepo.save(task1Assigned, assignedEvent)
```

Each line here applies a typestate transition from Chapter 4.

`task1Planned.allocate(...)` returns `(Task.Allocated, TaskEvent.TaskAllocated)`.
The method exists only on `Task.Planned`. If you tried to call `allocate` on a
`Task.Assigned`, the compiler would reject it. After allocation, the task
knows its source and destination locations.

`task1Allocated.assign(pickerId, at)` returns `(Task.Assigned,
TaskEvent.TaskAssigned)`. Again, `assign` exists only on `Task.Allocated`.
The task now knows which user is responsible for it.

After each transition, we persist the new state and event through the
repository. The events accumulate in `taskRepo.events`, building a complete
audit trail. We repeat the same two transitions for task 2.

System state after allocation and assignment:

| Entity | Count | State    |
| ------ | ----- | -------- |
| Wave   | 1     | Released |
| Task 1 | 1     | Assigned |
| Task 2 | 1     | Assigned |

Both tasks are ready for the picker.

## Step 4: Task Completion (Full Pick)

The picker arrives at the source location for task 1 and picks all 10 units of
SKU-A. The handheld device calls:

```scala
val result1 = completionService.complete(
  task1Assigned.id, 10, verified = true, at
)
```

This triggers the `TaskCompletionService` cascade from Chapter 7. The service
first runs three guard checks: the quantity must not be negative, the task
must exist and be in the `Assigned` state, and verification must be satisfied
(disabled in our setup). All three pass. Then the cascade begins:

```scala
val (completed, completedEvent) = assigned.complete(actualQuantity, at)
taskRepository.save(completed, completedEvent)

val shortpick = ShortpickPolicy(completed, at)
shortpick.foreach { (replacement, event) =>
  taskRepository.save(replacement, event)
}

val routing = RoutingPolicy(completedEvent, at)
routing.foreach { (pending, event) =>
  transportOrderRepository.save(pending, event)
}
// ... wave completion follows
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala (simplified)_</small>

**Cascade step 1: Complete the task.** `assigned.complete(10, at)` returns
`(Task.Completed, TaskEvent.TaskCompleted)`. The typestate transition from
Chapter 4. Persisted immediately.

**Cascade step 2: Stock consumption.** Skipped (no stock position repository).

**Cascade step 3: ShortpickPolicy.** `remainder = 10 - 10 = 0`. Zero is not
positive, so the policy returns `None`. Full pick, no shortage.

**Cascade step 4: RoutingPolicy.** Our task has no handling unit
(`handlingUnitId = None`), so `map` returns `None`. No transport order.

**Cascade step 5: WaveCompletionPolicy.** The service fetches all wave tasks.
Task 1 is now `Completed`, but task 2 is still `Assigned`.
`waveTasks.forall(isTerminal)` returns `false`. The wave stays `Released`.

**Result of completing task 1:**

```
completed:       Task.Completed (10 of 10)
shortpick:       None
transportOrder:  None
waveCompletion:  None
```

One down, one to go.

## Step 5: Task Completion with Shortpick

The picker moves to task 2, which requests 5 units of SKU-B. The bin is running
low. The picker can only find 3 units. The handheld device sends:

```scala
val result2 = completionService.complete(
  task2Assigned.id, 3, verified = true, at
)
```

The guards pass as before. The cascade begins, but this time the shortpick
policy has work to do.

**Cascade step 1: Complete the task.** `task2Assigned.complete(3, at)` returns
a `Task.Completed` with `requestedQuantity = 5` and `actualQuantity = 3`.

**Cascade step 2: Stock consumption.** Skipped.

**Cascade step 3: ShortpickPolicy.** `remainder = 5 - 3 = 2`. Two is
positive, so the policy fires:

```scala
object ShortpickPolicy:
  def apply(
      completed: Task.Completed,
      at: Instant
  ): Option[(Task.Planned, TaskEvent.TaskCreated)] =
    val remainder = completed.requestedQuantity - completed.actualQuantity
    if remainder <= 0 then None
    else
      Some(Task.create(
        taskType = completed.taskType,
        skuId = completed.skuId,
        packagingLevel = completed.packagingLevel,
        requestedQuantity = remainder,
        orderId = completed.orderId,
        waveId = completed.waveId,
        parentTaskId = Some(completed.id),
        handlingUnitId = completed.handlingUnitId,
        at = at
      ))
```

<small>_File: core/src/main/scala/neon/core/ShortpickPolicy.scala_</small>

It calls `Task.create` with `requestedQuantity = 2`, inheriting the SKU,
packaging level, order, and wave from the original task. Critically, it sets
`parentTaskId = Some(completed.id)`, linking the replacement to its parent for
traceability. The service persists this new `Task.Planned` immediately.

**Cascade step 4: RoutingPolicy.** No handling unit, returns `None`.

**Cascade step 5: WaveCompletionPolicy.** The service fetches all wave tasks.
There are now three: task 1 (`Completed`), task 2 (`Completed`), and the new
replacement task (`Planned`). The replacement is not terminal.
`waveTasks.forall(isTerminal)` returns `false`. The wave stays `Released`.

@:callout(info)

This is a subtle but important interaction. The shortpick
replacement task is persisted _before_ the wave completion check runs. This
means the policy sees the replacement in its task list. If the completion
check ran first, it would see only two completed tasks and close the wave
prematurely, leaving the remaining 2 units unfulfilled.

@:@

**Result of completing task 2:**

```
completed:       Task.Completed (3 of 5)
shortpick:       Some(Task.Planned, requestedQuantity = 2, parentTaskId = task2.id)
transportOrder:  None
waveCompletion:  None
```

System state:

| Entity           | State     | Details                  |
| ---------------- | --------- | ------------------------ |
| Wave             | Released  |                          |
| Task 1           | Completed | 10 of 10 (full)          |
| Task 2           | Completed | 3 of 5 (shortpick)       |
| Replacement task | Planned   | 2 units, parent = task 2 |

## Step 6: Completing the Replacement Task

The replacement task goes through the same lifecycle as any other task:
`Planned` to `Allocated` to `Assigned`, following the exact typestate
transitions we traced in Step 3. After allocation and assignment, the picker
finds the remaining 2 units and completes the task:

```scala
val result3 = completionService.complete(
  replacementAssigned.id, 2, verified = true, at
)
```

The cascade runs again. Steps 1 through 4 are uneventful: the task completes
(2 of 2), shortpick remainder is zero, no routing needed. But step 5 is
different this time.

**WaveCompletionPolicy.** The service fetches all wave tasks. There are now
three: task 1 (`Completed`), task 2 (`Completed`), and the replacement task
(`Completed`). Every task is terminal. `waveTasks.forall(isTerminal)` returns
`true`.

The policy calls `wave.complete(at)`, returning
`(Wave.Completed, WaveEvent.WaveCompleted)`. The wave transitions from
`Released` to `Completed`. The service persists both.

**Result of completing the replacement task:**

```
completed:       Task.Completed (2 of 2)
shortpick:       None
transportOrder:  None
waveCompletion:  Some(Wave.Completed, WaveEvent.WaveCompleted)
```

The wave is closed.

## The Full Picture

Let's visualize the entire flow:

```
Order 1 (10x SKU-A) ──┐
                       ├── WavePlanner.plan ──► WavePlan
Order 2 (5x SKU-B)  ──┘                            │
                                                    ▼
                                        WaveReleaseService.release
                                                    │
                                    ┌───────────────┼───────────────┐
                                    ▼               ▼               ▼
                              Wave.Released   Task 1: Planned   Task 2: Planned
                                    │               │               │
                                    │          allocate/assign  allocate/assign
                                    │               │               │
                                    │          Task 1: Assigned Task 2: Assigned
                                    │               │               │
                                    │          complete(10)    complete(3)
                                    │               │               │
                                    │          Task 1: Completed    │
                                    │          (full pick)          │
                                    │                          Task 2: Completed
                                    │                          (shortpick)
                                    │                               │
                                    │                          ShortpickPolicy
                                    │                               │
                                    │                      Replacement: Planned
                                    │                               │
                                    │                       allocate/assign
                                    │                               │
                                    │                      Replacement: Assigned
                                    │                               │
                                    │                         complete(2)
                                    │                               │
                                    │                      Replacement: Completed
                                    │                               │
                                    │◄──────────────────────────────┘
                                    │
                              WaveCompletionPolicy
                              (all tasks terminal)
                                    │
                                    ▼
                              Wave.Completed
```

Final system state:

| Entity               | State     | Quantities |
| -------------------- | --------- | ---------- |
| Wave                 | Completed |            |
| Task 1 (SKU-A)       | Completed | 10 of 10   |
| Task 2 (SKU-B)       | Completed | 3 of 5     |
| Replacement (SKU-B)  | Completed | 2 of 2     |
| Consolidation Groups | (none)    |            |
| Transport Orders     | (none)    |            |

Three tasks, one wave, zero open work. Every unit accounted for: 10 units of
SKU-A picked in full, 5 units of SKU-B picked across two tasks (3 + 2). The
event log in each repository records the complete history of every transition.

## Testing the Flow

The test suites for these services exercise this exact flow. Let's look at how
`WaveReleaseServiceSuite` sets up a release test:

```scala
describe("task creation"):
  it("creates tasks from task requests via TaskCreationPolicy"):
    val taskRepository = InMemoryTaskRepository()
    val wavePlan = WavePlanner.plan(
      List(singleOrder()), OrderGrouping.Single, at
    )
    val service = buildService(taskRepository = taskRepository)
    val result = service.release(wavePlan, at)
    assert(result.tasks.size == 1)
    val (planned, event) = result.tasks.head
    assert(planned.skuId == skuId)
    assert(planned.requestedQuantity == 10)
    assert(planned.waveId.value == wavePlan.wave.id)
```

<small>_File: core/src/test/scala/neon/core/WaveReleaseServiceSuite.scala_</small>

And `TaskCompletionServiceSuite` tests the shortpick cascade:

```scala
describe("when actual is less than requested"):
  it("creates Planned replacement for the unfulfilled remainder"):
    val taskRepository = InMemoryTaskRepository()
    val task = assignedTask(requestedQuantity = 10)
    taskRepository.store(task.id) = task
    val service = buildService(taskRepository = taskRepository)
    val result = service.complete(task.id, 7, true, at).value
    val (replacement, event) = result.shortpick.value
    assert(replacement.requestedQuantity == 3)
    assert(replacement.parentTaskId.value == task.id)
    assert(event.requestedQuantity == 3)
```

<small>_File: core/src/test/scala/neon/core/TaskCompletionServiceSuite.scala_</small>

Notice the test style: factory methods (`singleOrder()`, `assignedTask()`)
create test data with sensible defaults. In-memory repositories provide
persistence. Assertions check both the return value and the repository state.
No mocking frameworks, no test doubles beyond the in-memory adapters.

The suite also contains a test titled "when shortpick creates a replacement /
prevents wave completion" that captures the ordering guarantee we traced in
Step 5: the replacement is persisted before the wave completion check runs, so
a partial pick cannot prematurely close the wave.

## What We Learned

Every concept from Part II contributed to this flow.

**Opaque type IDs** (Chapter 3) ensured that a `WaveId` could never be passed
where a `TaskId` was expected. When the `ShortpickPolicy` set `parentTaskId =
Some(completed.id)`, the type system guaranteed that `completed.id` was a
`TaskId`, not an `OrderId` or a `SkuId`.

**Typestate encoding** (Chapter 4) prevented invalid transitions. We could not
complete a `Planned` task, release a `Completed` wave, or assign a `Cancelled`
task. The compiler caught these errors before any test ran.

**Events** (Chapter 5) recorded every state change as an immutable fact. The
in-memory repositories accumulated `WaveReleased`, `TaskCreated`,
`TaskAllocated`, `TaskAssigned`, `TaskCompleted`, and `WaveCompleted` events.
In production, these events drive projections, audit logs, and downstream
integrations.

**Policies** (Chapter 6) made pure decisions. `ShortpickPolicy` computed a
remainder. `WaveCompletionPolicy` checked a predicate. Neither touched a
repository or a clock. We could test each policy in isolation with a single
function call, and we could compose them into a cascade with confidence that
their decisions would not have hidden side effects.

**Services** (Chapter 7) orchestrated the cascade. `WaveReleaseService`
called `TaskCreationPolicy` and `ConsolidationGroupFormationPolicy`, then
persisted the results. `TaskCompletionService` ran five cascade steps in
sequence, each feeding into the next. The service layer was the only place
where repositories were read and written. The `Either[Error, Result]` return
type let callers handle errors without catching exceptions.

**Repositories** (Chapter 8) abstracted persistence behind port traits. We
ran the entire flow with in-memory maps and list buffers. Not a single line
of code needs to change when we swap in production implementations backed by
Pekko Cluster Sharding and PostgreSQL.

The domain model works beautifully in memory. But production systems need
actors, cluster sharding, and persistent storage. In Part III, we will build
the infrastructure layer that brings this domain model to life in a
distributed, event-sourced system.
