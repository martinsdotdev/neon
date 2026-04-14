# Repositories: The Dual Interface Pattern

In Chapter 7, we saw services reading and writing through repository traits
like `TaskRepository` and `WaveRepository`. But we never opened those traits to
look inside. Where does the data actually go? The answer depends on context: in
tests, it goes into a mutable map; in production, it flows through Pekko
Cluster Sharding to an event-sourced actor. The same service works with both,
because it depends only on the trait, never on the implementation. This is the
dual interface pattern.


## The Port Trait Pattern

Let's start with the simplest repository in the system:

```scala
package neon.wave

import neon.common.WaveId

/** Port trait for [[Wave]] aggregate persistence and queries. */
trait WaveRepository:
  def findById(id: WaveId): Option[Wave]
  def save(wave: Wave, event: WaveEvent): Unit
```

<small>*File: wave/src/main/scala/neon/wave/WaveRepository.scala*</small>

Two methods. That is the entire persistence interface for the wave aggregate.
`findById` retrieves a wave by its identifier, returning `Option[Wave]` because
the wave might not exist. `save` persists a wave together with the event that
caused the state transition. Neither method mentions a database, a connection
pool, an actor system, or any other infrastructure concept. The vocabulary is
pure domain: `WaveId`, `Wave`, `WaveEvent`.

Notice where this trait lives: in the `neon.wave` package, inside the `wave`
module. Not in an infrastructure module. Not in the `app` module. The
repository port belongs to the domain it serves. This is a deliberate choice.
The domain module defines *what* persistence operations it needs, and some
other module provides the *how*.

The Scaladoc comment says "Port trait." This naming is intentional. In
hexagonal architecture (which we will discuss later in this chapter), a *port*
is an interface that the application defines for communicating with the outside
world. The domain says "I need to find waves by ID and save them." It does not
say how that happens. That is the adapter's job.

@:callout(info)

A repository port always takes both the aggregate state and the
event in its `save` method. This is because the Pekko production
implementation needs the event for its event-sourced journal, while the
in-memory test implementation needs it for event tracking. The dual
requirement is baked into the signature.

@:@


## Sync vs Async: Two Traits for Two Worlds

Here is the async counterpart of `WaveRepository`:

```scala
package neon.wave

import neon.common.WaveId
import scala.concurrent.Future

/** Async port trait for [[Wave]] aggregate persistence and queries. */
trait AsyncWaveRepository:
  def findById(id: WaveId): Future[Option[Wave]]
  def save(wave: Wave, event: WaveEvent): Future[Unit]
```

<small>*File: wave/src/main/scala/neon/wave/AsyncWaveRepository.scala*</small>

The two traits define the same operations. The only difference is the return
type: `Option[Wave]` becomes `Future[Option[Wave]]`, and `Unit` becomes
`Future[Unit]`. Every operation in the async trait is wrapped in a `Future`,
because in production, repository calls cross the network to reach a Pekko
Cluster Sharding entity on a potentially different node.

Why not use only the async trait everywhere? Because sync tests are simpler,
faster, and more debuggable. Consider the difference:

```scala
// Sync test: direct and readable
val task = taskRepository.findById(taskId).value
assert(task.isInstanceOf[Task.Completed])
```

```scala
// If we had only async: needs Await, an executor, and timeout handling
val task = Await.result(taskRepository.findById(taskId), 5.seconds).value
assert(task.isInstanceOf[Task.Completed])
```

With synchronous repositories, tests run without an `ExecutionContext`, without
`Await.result` calls, without timing issues. You call a method, you get a
value, you assert on it. The test reads like a specification, not like
infrastructure plumbing.

The cost of maintaining two traits is low. Each trait is a handful of method
signatures. The sync services in the `core` module use sync traits. The async
services (also in `core`) use async traits. Both service variants contain the
same business logic; they differ only in how they sequence repository calls
(sequential statements vs. `Future` for-comprehensions). We will see this in
detail later in this chapter.


## A Richer Port: TaskRepository

Not every repository is as minimal as `WaveRepository`. Some aggregates need
more complex query patterns. Here is `TaskRepository`:

```scala
trait TaskRepository:
  def findById(id: TaskId): Option[Task]
  def findByWaveId(waveId: WaveId): List[Task]
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[Task]
  def save(task: Task, event: TaskEvent): Unit
  def saveAll(entries: List[(Task, TaskEvent)]): Unit
```

<small>*File: task/src/main/scala/neon/task/TaskRepository.scala*</small>

This trait has three kinds of operations:

**Single-entity lookup.** `findById` is the same pattern as `WaveRepository`.
Every repository has this method.

**Cross-entity queries.** `findByWaveId` returns all tasks belonging to a
wave. `findByHandlingUnitId` returns all tasks associated with a handling unit.
These methods exist because services need them. `WaveCompletionPolicy` (from
Chapter 6) needs every task in a wave to check whether they are all terminal.
The policy cannot answer the question without this data, and the data comes
through the repository.

**Batch persistence.** `saveAll` saves multiple tasks and events in a single
call. This is used during wave release, when `TaskCreationPolicy` produces a
batch of planned tasks that all need to be persisted at once.

The async counterpart mirrors every operation:

```scala
trait AsyncTaskRepository:
  def findById(id: TaskId): Future[Option[Task]]
  def findByWaveId(waveId: WaveId): Future[List[Task]]
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): Future[List[Task]]
  def save(task: Task, event: TaskEvent): Future[Unit]
  def saveAll(entries: List[(Task, TaskEvent)]): Future[Unit]
```

<small>*File: task/src/main/scala/neon/task/AsyncTaskRepository.scala*</small>

Note the Scaladoc on `saveAll` in the async trait: "Not transactional:
individual entries may succeed or fail independently." This is a critical
production constraint. In the Pekko implementation, `saveAll` fans out to
individual entity actors. Each actor persists its own events independently.
There is no distributed transaction wrapping the batch. If one actor fails,
the others may still succeed.

This is an honest interface. The trait does not promise transactional semantics
it cannot deliver. The caller knows that a partial failure is possible and can
design accordingly. The sync in-memory implementation, by contrast, is
inherently atomic (it runs in a single thread with a single mutable map), but
the interface contract is defined by the most constrained implementation:
production.

@:callout(info)

Cross-entity queries like `findByWaveId` behave differently in
production than in tests. The in-memory implementation filters a map. The
Pekko implementation queries a CQRS projection table, then fans out to
individual actors. The interface hides this complexity, but understanding
it matters for performance tuning. We will explore the production
implementation in Chapter 11.

@:@


## In-Memory Implementations for Testing

Here is the in-memory implementation of `TaskRepository`, taken directly from
the test suite:

```scala
class InMemoryTaskRepository extends TaskRepository:
  val store: mutable.Map[TaskId, Task] = mutable.Map.empty
  val events: mutable.ListBuffer[TaskEvent] = mutable.ListBuffer.empty

  def findById(id: TaskId): Option[Task] = store.get(id)
  def findByWaveId(waveId: WaveId): List[Task] =
    store.values.filter(_.waveId.contains(waveId)).toList
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[Task] =
    store.values.filter(_.handlingUnitId.contains(handlingUnitId)).toList
  def save(task: Task, event: TaskEvent): Unit =
    store(task.id) = task
    events += event
  def saveAll(entries: List[(Task, TaskEvent)]): Unit =
    entries.foreach((task, event) => save(task, event))
```

<small>*File: core/src/test/scala/neon/core/TaskCompletionServiceSuite.scala*</small>

Every method is one to three lines. `findById` delegates to `Map.get`.
`findByWaveId` filters the map values by a predicate. `save` puts the task in
the map and appends the event to a list buffer. `saveAll` loops over the
entries and delegates to `save`.

Two mutable collections drive the entire implementation:

**`mutable.Map[TaskId, Task]`** stores the current state of each task. When
`save` is called, the map is updated. If a task already exists, its state is
replaced. The map always reflects the latest state, just like a real database
would.

**`mutable.ListBuffer[TaskEvent]`** stores every event that was produced. This
list is append-only during a test. Unlike the map (which overwrites previous
state), the event buffer retains the full history.

Why track events separately? Because tests need to assert on *what happened*,
not just the final state. Consider a task completion test: after the service
runs, we want to verify that a `TaskCompleted` event was produced, that a
`TaskCreated` event was produced for the shortpick replacement, and that a
`TransportOrderCreated` event was produced for routing. The final state of the
task (it is `Completed`) tells us part of the story. The event buffer tells us
the rest.

Here is how the test suite uses these collections:

```scala
// Seed the repository with test data
val taskRepo = InMemoryTaskRepository()
val task = assignedTask()
taskRepo.store(task.id) = task

// Run the service
val result = service.complete(task.id, 7, true, at).value

// Assert on state
assert(taskRepo.store(task.id).isInstanceOf[Task.Completed])

// Assert on events
assert(taskRepo.events.exists(_.isInstanceOf[TaskEvent.TaskCompleted]))
assert(taskRepo.events.exists(_.isInstanceOf[TaskEvent.TaskCreated]))
```

The `store` field is public, so tests can seed initial state by writing
directly to the map. The `events` field is public, so tests can inspect the
full event history after the service runs. This is deliberate: in-memory
repositories are test infrastructure, not production code. They prioritize
transparency over encapsulation.

Every repository in the system follows this same in-memory pattern. Here is the
wave equivalent:

```scala
class InMemoryWaveRepository extends WaveRepository:
  val store: mutable.Map[WaveId, Wave] = mutable.Map.empty
  val events: mutable.ListBuffer[WaveEvent] = mutable.ListBuffer.empty
  def findById(id: WaveId): Option[Wave] = store.get(id)
  def save(wave: Wave, event: WaveEvent): Unit =
    store(wave.id) = wave
    events += event
```

<small>*File: core/src/test/scala/neon/core/TaskCompletionServiceSuite.scala*</small>

Four lines of implementation. The structural repetition is a feature. When you
have seen one in-memory repository, you have seen them all. A new aggregate
module can have a working test repository in under a minute.


## How Services Use Repositories

Let's revisit how services depend on repository traits. Here is the constructor
of `TaskCompletionService` from Chapter 7:

```scala
class TaskCompletionService(
    taskRepository: TaskRepository,
    waveRepository: WaveRepository,
    consolidationGroupRepository: ConsolidationGroupRepository,
    transportOrderRepository: TransportOrderRepository,
    verificationProfile: VerificationProfile
):
```

<small>*File: core/src/main/scala/neon/core/TaskCompletionService.scala*</small>

Four repository traits, injected through the constructor. The service does not
know whether it is talking to a mutable map or a Pekko actor cluster. It calls
`taskRepository.findById(taskId)` and gets back an `Option[Task]`. It calls
`waveRepository.save(wave, event)` and the wave is persisted somewhere.

In tests, the wiring looks like this:

```scala
val taskRepo = InMemoryTaskRepository()
val waveRepo = InMemoryWaveRepository()
val consolidationGroupRepo = InMemoryConsolidationGroupRepository()
val transportOrderRepo = InMemoryTransportOrderRepository()
val verificationProfile = VerificationProfile.default

val service = TaskCompletionService(
  taskRepo, waveRepo, consolidationGroupRepo,
  transportOrderRepo, verificationProfile
)
```

In production, the wiring happens in `ServiceRegistry` (Chapter 16), which
passes `PekkoTaskRepository`, `PekkoWaveRepository`, and so on. The service
class is identical in both cases. Same source file, same compiled bytecode,
same business logic. Only the adapters change.

This is the core benefit of the port trait pattern. The service embodies the
business rules. The repository adapters embody the persistence strategy. The
two concerns are completely decoupled. You can test business logic without a
database. You can swap persistence strategies without touching business logic.


## The Async Service Pattern

When a service needs to run in production with Pekko actors, it uses async
repositories and returns `Future` values. Here is `AsyncTaskCompletionService`:

```scala
class AsyncTaskCompletionService(
    taskRepository: AsyncTaskRepository,
    waveRepository: AsyncWaveRepository,
    consolidationGroupRepository: AsyncConsolidationGroupRepository,
    transportOrderRepository: AsyncTransportOrderRepository,
    verificationProfile: VerificationProfile
)(using ExecutionContext) extends LazyLogging:
```

<small>*File: core/src/main/scala/neon/core/AsyncTaskCompletionService.scala*</small>

The constructor mirrors the sync version, but every repository trait is the
`Async` variant. The `(using ExecutionContext)` is a Scala 3 context parameter:
`Future` needs an execution context to schedule its callbacks, and this
parameter lets the caller provide one without explicit passing at every call
site.

The business logic is the same as the sync version. The difference is in how
operations are sequenced. Where the sync service uses sequential statements,
the async service uses a `for`-comprehension over `Future`:

```scala
private def completeAssigned(
    assigned: Task.Assigned,
    actualQuantity: Int,
    at: Instant
): Future[Either[TaskCompletionError, TaskCompletionResult]] =
  val (completed, completedEvent) = assigned.complete(actualQuantity, at)
  for
    _ <- taskRepository.save(completed, completedEvent)
    shortpick <- persistShortpick(completed, at)
    routing <- persistRouting(completedEvent, at)
    (waveCompletion, pickingCompletion) <-
      detectWaveAndPickingCompletion(completed, at)
  yield Right(
    TaskCompletionResult(
      completed, completedEvent, shortpick, routing,
      waveCompletion, pickingCompletion
    )
  )
```

<small>*File: core/src/main/scala/neon/core/AsyncTaskCompletionService.scala*</small>

Let's read this line by line.

The first line is synchronous and pure: `assigned.complete(actualQuantity, at)`
is a typestate transition method (Chapter 4) that returns a `(Completed, Event)`
tuple. No `Future` here. The domain logic does not change just because we are
in an async context.

Then the `for`-comprehension sequences four async operations:

1. **Save the completed task.** The `_ <-` discards the `Unit` result; we
   only care that the save succeeds.
2. **Persist the shortpick replacement** (if any). `persistShortpick` calls
   `ShortpickPolicy`, and if the policy returns `Some`, saves the replacement
   task through the async repository.
3. **Persist the routing transport order** (if any). Same pattern: call
   `RoutingPolicy`, save if there is a result.
4. **Detect wave and picking completion.** This step calls
   `taskRepository.findByWaveId` to load all wave tasks, then runs
   `WaveCompletionPolicy` and `PickingCompletionPolicy`.

Each `<-` arrow in the `for`-comprehension is a `flatMap` under the hood. If
any step fails (the `Future` completes with an exception), the remaining steps
are skipped. The `yield` at the end wraps the successful result in `Right`.

The important insight is this: the policies themselves (`ShortpickPolicy`,
`RoutingPolicy`, `WaveCompletionPolicy`) are still synchronous and pure. They
are called inside async helper methods, but the policy invocation is a simple
function call that returns immediately. Only the repository interactions are
async. The domain logic sits at the center, untouched by the async machinery
around it.


## Architecture Note: Hexagonal Architecture

The repository pattern in Neon WES is a direct application of Alistair
Cockburn's hexagonal architecture, also known as Ports and Adapters. The core
idea: the application defines interfaces (ports) for everything it needs from
the outside world, and different implementations (adapters) plug into those
interfaces for different contexts.

In Neon WES, the pieces map as follows:

**Driven ports** are the repository traits. The application *drives* outward
through them to persist and query data. `WaveRepository`, `TaskRepository`,
`AsyncWaveRepository`, `AsyncTaskRepository` are all driven ports.

**Adapters for driven ports** are the concrete implementations.
`InMemoryTaskRepository` is a test adapter. `PekkoTaskRepository` (Chapter 11)
is the production adapter. Both implement the same port trait.

**Driving adapters** sit on the other side. HTTP routes (Chapter 13) are
driving adapters: they receive external requests and translate them into
service calls. The service does not know whether it was called by an HTTP
request, a test method, or a scheduler.

**The dependency rule** enforces the direction of knowledge. Domain modules
(`wave`, `task`, `consolidation-group`) define port traits. They import nothing
from Pekko, nothing from HTTP, nothing from any database library. The `core`
module depends on domain modules and uses their port traits. The `app` module
provides the adapters and wires everything together.

This rule is visible in the import statements. Open any file in `core` and you
will find imports like `neon.task.TaskRepository` and `neon.wave.WaveRepository`.
You will never find `org.apache.pekko` or `io.r2dbc`. The `core` module is
pure domain orchestration. Infrastructure lives in `app`.

**`ServiceRegistry`** (Chapter 16) is the *composition root*: the single place
where adapters are created and wired to ports. In test suites, the test itself
is the composition root. In production, `ServiceRegistry` does the wiring
inside the `app` module.

Here is how the layers connect:

```
[HTTP Routes]  -->  [Services]  -->  [Repository Traits]
  (driving           (core)          (driven ports)
   adapter)                               |
                                    ______│______
                                   |             |
                              [InMemory]   [Pekko Adapter]
                              (test)       (production)
```

The services in the center depend only on the port traits (the thin line
pointing right). The adapters on the bottom implement those traits. The HTTP
routes on the left call into services. At no point does any arrow point from
a domain module toward an infrastructure module. Dependencies always point
inward, toward the domain.

This is why we can test business logic without starting a database, without
booting an actor system, without configuring a network. The in-memory adapters
are trivial to construct, and the services cannot tell the difference. The
hexagonal boundary is not just an architectural diagram; it is enforced by the
module dependency graph and by the import statements in every source file.


## The Complete Repository Catalogue

Let's survey all the repository traits in the system. Each event-sourced
aggregate defines both a sync and an async port:

| Aggregate             | Sync Port                        | Async Port                            |
|-----------------------|----------------------------------|---------------------------------------|
| Wave                  | `WaveRepository`                 | `AsyncWaveRepository`                 |
| Task                  | `TaskRepository`                 | `AsyncTaskRepository`                 |
| ConsolidationGroup    | `ConsolidationGroupRepository`   | `AsyncConsolidationGroupRepository`   |
| TransportOrder        | `TransportOrderRepository`       | `AsyncTransportOrderRepository`       |
| HandlingUnit          | `HandlingUnitRepository`         | `AsyncHandlingUnitRepository`         |
| Workstation           | `WorkstationRepository`          | `AsyncWorkstationRepository`          |
| Slot                  | `SlotRepository`                 | `AsyncSlotRepository`                 |
| StockPosition         | `StockPositionRepository`        | (wired through sync in current scope) |

Every sync port follows the same structure: `findById` for single-entity
lookup, `save` for persisting a state-event pair, and optional query methods
(`findByWaveId`, `findByHandlingUnitId`) where services need them. Every async
port mirrors the sync one with `Future` wrappers.

The consistency across ports means that learning one repository teaches you
all of them. A developer adding a new aggregate module (Chapter 20) can look
at `WaveRepository` as a template and have a working port trait in minutes.


## What Comes Next

We have now covered the complete domain layer: types (Chapter 3), state
machines (Chapter 4), events (Chapter 5), policies (Chapter 6), services
(Chapter 7), and repositories (Chapter 8). In the next chapter, we will bring
it all together by tracing the complete wave release flow from order selection
through task creation, allocation, assignment, and completion, seeing every
layer collaborate in a single end-to-end scenario.
