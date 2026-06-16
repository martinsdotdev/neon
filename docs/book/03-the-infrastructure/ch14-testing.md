# Testing at Every Layer

A warehouse execution system cannot afford surprises. When a picker scans a
tote and the system says "task completed," that completion must cascade
correctly through shortpick detection, transport order creation, wave
completion checking, and consolidation group state updates. If any link in
that chain breaks silently, physical goods end up in the wrong place.

Testing in Neon WES is organized as a pyramid with seven distinct layers.
Each layer tests a different concern, uses different tools, and catches
different categories of bugs. The lower layers are fast, pure, and
numerous. The upper layers are slower, involve real infrastructure, and are
fewer in count. Together they provide confidence that every state transition,
every policy decision, and every service orchestration works correctly.

In this chapter we will walk through each layer, study the conventions that
keep tests consistent, and examine real test suites from the codebase.

## The Testing Pyramid in Neon WES

Here is how the seven layers stack up, from the base (fastest, most numerous)
to the peak (slowest, fewest):

```
                    +---------------------------+
                    |  7. Projection handler    |
                    |     tests (Testcontainers)|
                  +-------------------------------+
                  |  6. HTTP route tests          |
                  |     (ScalatestRouteTest)       |
                +-----------------------------------+
                |  5. Pekko repository integration  |
                |     tests (single-node cluster)   |
              +---------------------------------------+
              |  4. Actor tests                       |
              |     (EventSourcedBehaviorTestKit)      |
            +-------------------------------------------+
            |  3. Service tests                         |
            |     (in-memory repositories)               |
          +-----------------------------------------------+
          |  2. Policy tests                              |
          |     (pure functions, zero dependencies)        |
        +---------------------------------------------------+
        |  1. Domain aggregate tests                        |
        |     (pure Scala, no framework beyond ScalaTest)    |
        +---------------------------------------------------+
```

**Layer 1: Domain aggregate tests** verify that typestate transitions produce
the correct new state and event. Pure Scala; no actors, no repositories, no
mocking.

**Layer 2: Policy tests** verify that stateless decision functions return the
correct `Option[(State, Event)]` for every input scenario. Also pure Scala.

**Layer 3: Service tests** verify orchestration logic using in-memory
repository implementations. They assert on both the returned result and the
side effects recorded in the repositories.

**Layer 4: Actor tests** verify command handling, event persistence, state
recovery after restart, and serialization correctness using Pekko's
`EventSourcedBehaviorTestKit`.

**Layer 5: Pekko repository integration tests** verify that the
`PekkoXxxRepository` implementations correctly send commands to sharded
actors and aggregate results from projection queries.

**Layer 6: HTTP route tests** verify request parsing, response formatting,
status code mapping, and authentication using Pekko's `ScalatestRouteTest`.

**Layer 7: Projection handler tests** verify that event handlers correctly
insert and update read-side PostgreSQL tables, using Testcontainers to spin
up a real database.

@:callout(info)

Layers 1 through 3 make up the vast majority of tests. They run
in milliseconds, require no infrastructure, and cover the core business
logic. Layers 4 through 7 are important but intentionally fewer, because
the domain logic they exercise has already been validated by the lower layers.

@:@

## Conventions

Before we look at specific tests, let's establish the conventions that every
test suite in Neon WES follows.

### ScalaTest AnyFunSpec with describe/it

All suites use `AnyFunSpec` with nested `describe`/`it` blocks. This gives
us a BDD-style structure that reads like a specification:

```scala
class ShortpickPolicySuite extends AnyFunSpec with OptionValues:

  describe("ShortpickPolicy"):
    describe("when actual meets requested"):
      it("does not create a replacement task"):
        // ...

    describe("when actual is less than requested"):
      it("returns replacement task for the unfulfilled quantity"):
        // ...
```

The `describe` blocks group related scenarios. The `it` blocks are individual
test cases. When tests run, ScalaTest prints a tree-structured report:

```
ShortpickPolicySuite:
  ShortpickPolicy
    when actual meets requested
      - does not create a replacement task
    when actual is less than requested
      - returns replacement task for the unfulfilled quantity
      - copies wave, SKU, task type, and order ID from original
```

### Suite Naming

Test suites are named `<ComponentName>Suite`. Not `Test`, not `Spec`,
always `Suite`:

- `ShortpickPolicySuite` (policy)
- `TaskCompletionServiceSuite` (service)
- `WaveActorSuite` (actor)
- `WaveRoutesSuite` (HTTP route)
- `TaskProjectionHandlerSuite` (projection handler)

### Mix-in Traits

Suites mix in `OptionValues` and `EitherValues` from ScalaTest as needed.
These traits provide `.value` extractors that produce clear failure messages:

```scala
class ShortpickPolicySuite extends AnyFunSpec with OptionValues:
  // policy returns Option[(Task.Planned, TaskEvent.TaskCreated)]
  val (replacement, event) = ShortpickPolicy(task, at).value
  // .value on None fails with "The Option on which value was invoked was not defined."
```

```scala
class TaskCompletionServiceSuite extends AnyFunSpec
    with OptionValues with EitherValues with DomainFactories:
  // service returns Either[TaskCompletionError, TaskCompletionResult]
  val result = service.complete(taskId, 5, true, at).left.value
  // .left.value on Right fails with a clear message showing the Right value
```

### Factory Methods

Rather than redefining factory methods in every suite, the core service tests
share them through a single `DomainFactories` trait. A suite mixes the trait in
and gets a set of factory methods that create domain objects in specific states.
These methods have sensible defaults for every parameter (including
suite-level shared identifiers, so a `task`, its `wave`, and its
`consolidationGroup` agree out of the box), so individual tests only override
the values they care about:

```scala
trait DomainFactories:
  val skuId: SkuId = SkuId()
  val userId: UserId = UserId()
  val orderId: OrderId = OrderId()
  val waveId: WaveId = WaveId()
  // ... more shared identifiers

  def assignedTask(
      id: TaskId = TaskId(),
      requestedQuantity: Int = 10,
      orderId: OrderId = orderId,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId),
      stockPositionId: Option[StockPositionId] = None
  ): Task.Assigned =
    Task.Assigned(
      id = id,
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = requestedQuantity,
      orderId = orderId,
      waveId = waveId,
      parentTaskId = None,
      handlingUnitId = handlingUnitId,
      stockPositionId = stockPositionId,
      sourceLocationId = sourceLocationId,
      destinationLocationId = destinationLocationId,
      assignedTo = userId
    )

  def releasedWave(id: WaveId = waveId): Wave.Released =
    Wave.Released(id, OrderGrouping.Single, List(orderId))
```

<small>_File: core/src/test/scala/neon/core/DomainFactories.scala_</small>

This pattern keeps tests readable. Instead of constructing a full
`Task.Assigned` with thirteen parameters in every test, we write
`assignedTask(requestedQuantity = 5)` and let the defaults handle the rest. A
suite opts in by extending `DomainFactories` alongside its ScalaTest base, for
example `class TaskCompletionServiceSuite extends AnyFunSpec with OptionValues
with EitherValues with DomainFactories`.

## Layers 1 and 2: Domain and Policy Tests

These two layers are the foundation. They test pure functions with no
external dependencies. Let's look at each one.

### Layer 1: Domain Aggregate Tests

Domain aggregate tests verify that typestate transitions work correctly.
They call methods like `Task.create`, `Task.Planned.allocate`, and
`Wave.Released.complete`, then assert on the resulting state and event.

These tests are found in each domain module's test directory (for example,
`wave/src/test/scala/neon/wave/WaveSuite.scala` and
`task/src/test/scala/neon/task/TaskSuite.scala`). They use nothing beyond
ScalaTest and the domain types themselves.

### Layer 2: Policy Tests

Policies are pure functions that take a domain state and return
`Option[(NewState, Event)]`. They are the easiest code to test because
there is no setup, no teardown, and no side effects.

Here is `ShortpickPolicySuite`, which tests the policy that decides whether
a shortpicked task needs a replacement:

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
```

<small>_File: core/src/test/scala/neon/core/ShortpickPolicySuite.scala_</small>

The `completed` factory creates a `Task.Completed` with configurable
requested and actual quantities. Now let's see the test cases:

```scala
  describe("ShortpickPolicy"):
    describe("when actual meets requested"):
      it("does not create a replacement task"):
        assert(ShortpickPolicy(completed(10, 10), at).isEmpty)

    describe("when actual exceeds requested"):
      it("does not create a replacement task"):
        assert(ShortpickPolicy(completed(10, 12), at).isEmpty)

    describe("when actual is zero"):
      it("returns replacement task with full requested quantity"):
        val (replacement, _) =
          ShortpickPolicy(completed(10, 0), at).value
        assert(replacement.requestedQuantity == 10)

    describe("when actual is less than requested"):
      val task = completed(10, 7)
      val (replacement, event) =
        ShortpickPolicy(task, at).value

      it("returns replacement task for the unfulfilled quantity"):
        assert(replacement.requestedQuantity == 3)

      it("copies wave, SKU, task type, and order ID from original"):
        assert(replacement.waveId == task.waveId)
        assert(replacement.skuId == task.skuId)
        assert(replacement.taskType == task.taskType)
        assert(replacement.orderId == task.orderId)

      it("sets parentTaskId to the original task's ID"):
        assert(replacement.parentTaskId.value == task.id)

      it("preserves handling unit ID in replacement task"):
        assert(replacement.handlingUnitId == Some(handlingUnitId))

      it("TaskCreated event mirrors all replacement task fields"):
        assert(event.taskId == replacement.id)
        assert(event.requestedQuantity == 3)
        assert(event.parentTaskId.value == task.id)
```

<small>_File: core/src/test/scala/neon/core/ShortpickPolicySuite.scala_</small>

Notice several things about this test suite:

**Every scenario is a one-liner.** The policy is pure, so each test is just
"call the function, assert on the result." No setup, no mocking, no async
handling.

**Boundary conditions are explicit.** The suite tests full pick (10/10),
overpick (12/10), zero pick (0/10), and partial pick (7/10). Each boundary
has its own `describe` block.

**Field-level assertions.** The "when actual is less than requested" block
checks every field of the replacement task and event, catching subtle bugs
where one field is accidentally swapped or omitted.

## Layer 3: Service Tests

Service tests are where we verify orchestration: that a service calls the
right policies, persists through the right repositories, and returns the
correct result or error. The key tool here is the **in-memory repository**.

### In-Memory Repositories

The service tests share a family of in-memory repository implementations, one
standalone file per repository (`InMemoryTaskRepository`,
`InMemoryWaveRepository`, and so on), rather than redefining them inside each
suite. These are trivial classes backed by mutable maps, with an event buffer
for tracking side effects:

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

<small>_File: core/src/test/scala/neon/core/InMemoryTaskRepository.scala_</small>

The `store` map gives us the current state of every entity. The `events`
buffer lets us assert on what was persisted. This same pattern appears across
all the synchronous in-memory repositories in the codebase.

For the async services there is a parallel family of call-recording
repositories (`InMemoryAsyncTaskRepository`, `InMemoryAsyncWaveRepository`,
...). These share a single `CallRecorder` instance so a suite can assert on
the _ordering_ of cross-repository effects, for example that the task was saved
before the stock position, and that conditional loads were skipped:

```scala
final class CallRecorder:
  val entries: mutable.ListBuffer[String] = mutable.ListBuffer.empty
  def record(entry: String): Unit = entries += entry
  def saves: List[String] = entries.filter(_.endsWith(".save")).toList
  def contains(entry: String): Boolean = entries.contains(entry)
```

<small>_File: core/src/test/scala/neon/core/CallRecorder.scala_</small>

Service test suites also use a `buildService` factory that accepts optional
repository overrides, so each test passes in only the repositories it needs
to inspect and lets defaults handle the rest.

### Example: TaskCompletionServiceSuite

Task completion is the most intricate orchestration in the system: completing a
task can cascade into shortpick detection, stock consumption, transport-order
routing, wave completion, and consolidation-group completion. To keep that
testable, the logic is split. The pure cascade _decisions_ (given pre-loaded
state, what should happen) live in a separate decision module exercised by
`TaskCompletionCascadeSuite`. `TaskCompletionServiceSuite` is then a thinner
_shell_ suite: it verifies wiring, persistence effects, and end-to-end smoke
paths, not the decision logic itself. Here is the shape of that shell suite:

```scala
describe("TaskCompletionService"):
  describe("validation wiring"):
    it("returns TaskNotFound when the task does not exist"):
      val missingId = TaskId()
      val service = buildService()
      val result =
        service.complete(taskId = missingId, actualQuantity = 5, verified = true, at = at)
      assert(result.left.value == TaskCompletionError.TaskNotFound(missingId))

  describe("persistence"):
    it("persists Completed state"):
      val taskRepository = InMemoryTaskRepository()
      val task = assignedTask(requestedQuantity = 10)
      taskRepository.store(task.id) = task
      val service = buildService(taskRepository = taskRepository)
      service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
      assert(taskRepository.store(task.id).isInstanceOf[Task.Completed])
```

<small>_File: core/src/test/scala/neon/core/TaskCompletionServiceSuite.scala_</small>

The first test verifies the validation wiring (a nonexistent task yields the
correct error). The second seeds an `assignedTask` from `DomainFactories` and
checks that, after `complete`, the repository holds a `Task.Completed`, an
assertion on a persisted side effect, not just the return value.

There is also an async counterpart, `AsyncTaskCompletionServiceSuite`, which
exercises the `Future`-returning service over the call-recording async
repositories, verifying that the load/decide/persist shell drives the same
cascade and persists in the right order. Because the synchronous and
asynchronous services are thin shells over one shared decision module, the two
cannot drift.

Service tests also assert on repository side effects more broadly. In
`WaveReleaseServiceSuite`, after calling `service.release(wavePlan, at)`, we
verify both the return value (`result.tasks.size == 1`) and the repository
state (`waveRepository.store(wavePlan.wave.id).isInstanceOf[Wave.Released]`,
`waveRepository.events.size == 1`). This double-checking ensures the service
actually persisted its work, not just returned the right answer.

## Layer 4: Actor Tests

Actor tests verify the event-sourced behavior of Pekko actors. They use
`EventSourcedBehaviorTestKit`, which runs the actor in-process (no cluster
required) and gives us access to persisted events, state after each command,
and reply messages.

### Test Setup

Here is how `WaveActorSuite` sets up the test kit:

```scala
class WaveActorSuite
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString("""
          pekko.actor {
            provider = local
            serialization-bindings {
              "neon.common.serialization.CborSerializable" = jackson-cbor
            }
          }
        """)
        .withFallback(EventSourcedBehaviorTestKit.config)
        .resolve()
    )
    with AnyFunSpecLike
    with BeforeAndAfterEach:

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    WaveActor.Command,
    WaveEvent,
    WaveActor.State
  ](
    system,
    WaveActor(waveId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()
```

<small>_File: wave/src/test/scala/neon/wave/WaveActorSuite.scala_</small>

Key details:

**Local provider, not cluster.** The test uses `provider = local`, so no
cluster is needed. The `EventSourcedBehaviorTestKit.config` provides an
in-memory journal and snapshot store.

**Serialization verification.** Even though we run locally,
`withVerifyEvents(true)` and `withVerifyState(true)` tell the test kit to
serialize and deserialize every event and state snapshot. This catches
serialization bugs (missing Jackson annotations, unregistered types) without
needing a real cluster.

**Clear before each test.** `esTestKit.clear()` wipes the in-memory journal
between tests, so each test starts with a fresh actor.

### Testing Commands

Each test sends a command and inspects the result:

```scala
describe("WaveActor"):
  describe("Create"):
    it("persists WaveReleased event and sets Released state"):
      val planned =
        Wave.Planned(waveId, OrderGrouping.Single, orderIds)
      val event = WaveEvent.WaveReleased(
        waveId, OrderGrouping.Single, orderIds, at
      )
      val result =
        esTestKit.runCommand[StatusReply[Done]](
          WaveActor.Create(planned, event, _)
        )
      assert(result.event == event)
      assert(
        result.stateOfType[WaveActor.ActiveState]
          .wave.isInstanceOf[Wave.Released]
      )
```

<small>_File: wave/src/test/scala/neon/wave/WaveActorSuite.scala_</small>

`runCommand` sends the command to the actor and returns a
`CommandResultWithReply` that exposes:

- `result.event`: the persisted event
- `result.stateOfType[S]`: the actor state after the event was applied
- `result.reply`: the reply sent back to the caller
- `result.hasNoEvents`: asserts that no event was persisted (for rejection
  cases)

### Testing Rejections and Event Replay

Invalid commands should be rejected without persisting events. The
`hasNoEvents` assertion verifies this: `result.hasNoEvents` fails if the
actor accidentally persisted something. A rejected command must never write
to the event log.

One of the most valuable actor tests verifies recovery after restart:

```scala
    describe("event replay"):
      it("recovers Released state from journal"):
        createWave()
        esTestKit.restart()
        val result =
          esTestKit.runCommand[Option[Wave]](WaveActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[Wave.Released])
```

<small>_File: wave/src/test/scala/neon/wave/WaveActorSuite.scala_</small>

`esTestKit.restart()` stops the actor and starts a fresh instance that
replays events from the in-memory journal. After replay, the state should
be identical to what it was before the restart. This tests the `eventHandler`
function in isolation and catches bugs where the event handler does not
reconstruct state correctly.

## Layers 5 through 7: Integration Tests

The upper layers of the pyramid test infrastructure integration. They are
slower but catch problems that unit tests cannot.

### Layer 5: Pekko Repository Integration Tests

These tests verify that `PekkoXxxRepository` implementations correctly
interact with sharded actors. Suites like `PekkoWaveRepositorySuite` and
`PekkoTaskRepositorySuite` extend `ScalaTestWithActorTestKit`, configure a
single-node cluster, and verify that queries return correct results. They
catch serialization issues and sharding configuration errors without needing
multiple nodes.

### Layer 6: HTTP Route Tests

Route tests use `ScalatestRouteTest` from Pekko HTTP's test kit. They
send HTTP requests to the route tree and assert on status codes, headers,
and response bodies:

```scala
class WaveRoutesSuite extends AnyFunSpec with ScalatestRouteTest with RouteSuiteBase:

  describe("WaveRoutes"):
    describe("DELETE /waves/:id"):
      it("returns 200 with cancellation response on success"):
        val routes = WaveRoutes(
          stubCancellationService(Right(result)),
          stubPlanningService(Right(null)),
          stubOrderRepo,
          authService
        )
        val request = Delete(s"/waves/${waveId.value}")
          .addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json = parse(responseAs[String])
            .getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("cancelled")
          )
        }

      it("returns 404 when wave not found"):
        val routes = WaveRoutes(
          stubCancellationService(
            Left(WaveCancellationError.WaveNotFound(waveId))
          ),
          stubPlanningService(Right(null)),
          stubOrderRepo,
          authService
        )
        val request = Delete(s"/waves/${waveId.value}")
          .addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
          assert(contentType.mediaType.toString == "application/problem+json")
          val json = parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor.get[String]("type").exists(_.startsWith("urn:neon:error:"))
          )
        }

      it("returns 401 without session cookie"):
        Delete(s"/waves/${waveId.value}") ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }
```

<small>_File: app/src/test/scala/neon/app/http/WaveRoutesSuite.scala_</small>

Route tests use stub services (anonymous subclasses that return canned
`Either` values) so they focus purely on HTTP behavior: request routing,
JSON serialization, status codes, content type, and cookie handling. Notice
the error-path test asserts not just the 404 status but also the
`application/problem+json` content type and the `urn:neon:error:` `type` field,
confirming the route went through `completeProblem` and `ProblemRouteHandlers`.

Auth scaffolding comes from the shared `RouteSuiteBase` trait, which every
route suite mixes in. It wires a real `AuthenticationService` over in-memory
user, session, and permission repositories, seeds an Admin user holding every
permission, and exposes a logged-in `sessionToken` obtained through a genuine
`login` call, so tests verify the full auth flow without hitting a database and
without each suite re-building the same scaffolding.

### Layer 7: Projection Handler Tests

Projection handler tests verify SQL operations against a real PostgreSQL
database. They use `PostgresContainerSuite`, which starts a Testcontainers
PostgreSQL instance and creates the required tables:

```scala
class TaskProjectionHandlerSuite extends PostgresContainerSuite:

  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private val handler = TaskProjectionHandler()

  describe("TaskProjectionHandler"):
    it("inserts into task_by_wave on TaskCreated"):
      val taskId = TaskId()
      val event = TaskEvent.TaskCreated(/* ... */)

      withSession { session =>
        handler
          .process(
            session,
            envelope(event = event, persistenceId = s"Task|${taskId.value}", entityType = "Task")
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM task_by_wave " +
          s"WHERE task_id = '${taskId.value}'"
      )
      assert(count == 1L)
```

<small>_File: app/src/test/scala/neon/app/projection/TaskProjectionHandlerSuite.scala_</small>

The `TaskProjectionHandler` constructor needs an `ExecutionContext` and an
`ActorSystem[?]` (recall from Chapter 12 that it publishes notifications to the
event stream). The suite provides the former as a `given` and inherits the
latter, plus the `withSession`, `queryCount`, and `envelope` helpers, from
`PostgresContainerSuite`.

These tests catch SQL syntax errors, incorrect column bindings, and
`ON CONFLICT` clause issues that cannot be caught without a real database.
The `PostgresContainerSuite` base class manages the container lifecycle and
provides `withSession` and `queryCount` helpers.

@:callout(info)

Domain and actor tests live in their respective module's test
directory, while routes, projection handlers, and integration tests live
in the `app` module.

@:@

## What Comes Next

We have now covered the full Neon WES backend, from domain aggregates through
event sourcing, CQRS projections, HTTP API, and testing. In the next chapter,
we will put it all together in a project that simulates a complete warehouse
day: receiving inbound deliveries, processing waves, handling shortpicks,
and closing out the shift.
