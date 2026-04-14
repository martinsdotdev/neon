# Cluster Sharding and Persistence

In Chapter 10, we built an event-sourced actor for the Wave aggregate. A single
`WaveActor` can persist events, replay them on recovery, and handle commands
through the ask pattern. But in production, we might have millions of waves,
each with its own actor. How do we route a command to the correct actor,
potentially running on a different cluster node? The answer is Pekko Cluster
Sharding.

Cluster Sharding solves a deceptively hard problem: given an entity identifier
(like a WaveId), find the actor responsible for that entity, start it if it does
not exist yet, and deliver the command, regardless of which node in the cluster
the actor happens to live on. The caller does not need to know the actor's
physical location. Sharding handles all of it.

In this chapter, we will see how `PekkoRepository` bridges the domain layer to
the cluster, how cross-entity queries combine CQRS projection tables with actor
fan-out, and how to test actors with `EventSourcedBehaviorTestKit` without a
running cluster.


## Cluster Sharding in 5 Minutes

Before we look at code, let's build a mental model with four concepts.

**EntityTypeKey** identifies a *type* of entity. In Neon WES, each aggregate has
its own key: `"Wave"`, `"Task"`, `"ConsolidationGroup"`, and so on. The key is
defined as a constant in the actor's companion object:

```scala
val EntityKey: EntityTypeKey[Command] =
  EntityTypeKey[Command]("Wave")
```

<small>*File: wave/src/main/scala/neon/wave/WaveActor.scala*</small>

The type parameter `[Command]` tells sharding what message type this entity
accepts. The string `"Wave"` is the logical name used for persistence IDs and
shard routing.

**Entity ID** identifies a *specific instance* within a type. For waves, this
is the UUID rendered as a string. For tasks, it is the task's UUID string. The
entity ID is the second coordinate in the two-dimensional address space that
sharding maintains: type key plus entity ID uniquely identifies one actor in the
entire cluster.

**EntityRef** is a reference to a specific entity. You obtain one by calling
`sharding.entityRefFor(EntityKey, entityId)`. An `EntityRef` behaves like a
typed `ActorRef`, but with a critical difference: it routes messages through the
sharding infrastructure. If the target actor is not running, sharding starts it.
If it lives on another node, sharding delivers the message over the network. The
caller never has to think about any of this.

**Shard management** is the lifecycle layer underneath. Sharding divides
entities into shards (groups), distributes shards across cluster nodes, and
handles rebalancing when nodes join or leave. Actors that have been idle for
a configurable period are *passivated* (stopped to free memory) and will be
restarted on demand when the next message arrives. From the caller's
perspective, the entity is always "there"; sharding makes it so.

These four concepts form a clean addressing scheme:

```
EntityTypeKey("Wave") + EntityId("550e8400-...") --> EntityRef --> WaveActor
```

The caller says "I want the Wave entity with this ID." Sharding returns a
reference. That reference might point to an actor on the local node, on a
remote node, or one that does not exist yet. The caller does not need to
distinguish between these cases.


## The PekkoRepository Pattern

With the sharding concepts in place, let's see how Neon WES uses them in
practice. The bridge between the domain layer and the actor cluster is the
`PekkoRepository`: a class that implements the `AsyncRepository` port trait
from Chapter 8 by translating each repository method into a Cluster Sharding
ask.

Here is `PekkoWaveRepository` in full:

```scala
class PekkoWaveRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncWaveRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(Entity(WaveActor.EntityKey)(ctx => WaveActor(ctx.entityId)))

  def findById(id: WaveId): Future[Option[Wave]] =
    sharding
      .entityRefFor(WaveActor.EntityKey, id.value.toString)
      .ask(WaveActor.GetState(_))

  def save(wave: Wave, event: WaveEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      WaveActor.EntityKey,
      wave.id.value.toString
    )
    event match
      case e: WaveEvent.WaveReleased =>
        entityRef
          .askWithStatus(
            WaveActor.Create(Wave.Planned(wave.id, wave.orderGrouping, e.orderIds), e, _)
          )
          .map(_ => ())
      case e: WaveEvent.WaveCompleted =>
        entityRef
          .askWithStatus[WaveActor.CompleteResponse](
            WaveActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: WaveEvent.WaveCancelled =>
        entityRef
          .askWithStatus[WaveActor.CancelResponse](
            WaveActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
```

<small>*File: wave/src/main/scala/neon/wave/PekkoWaveRepository.scala*</small>

Let's walk through this piece by piece.

**Constructor and initialization.** The repository receives an `ActorSystem` and
an implicit `Timeout` (for ask operations). In the constructor body, it calls
`sharding.init(Entity(...))`, which registers the Wave entity type with Cluster
Sharding and tells it how to create actors. The factory function
`ctx => WaveActor(ctx.entityId)` receives the entity context and passes the
entity ID string to the `WaveActor.apply` method we saw in Chapter 10.

This `init` call is idempotent. If another part of the system has already
initialized the Wave entity type, the call is a no-op. This is important because
multiple components might need Wave entity references, and each one calls `init`
defensively.

**`findById`** gets an `EntityRef` for the requested wave ID, sends a `GetState`
command via `ask`, and returns the `Future[Option[Wave]]` reply. If the actor
does not exist yet, sharding starts it, it initializes with `EmptyState`, and
replies with `None`. If the actor holds a wave, it replies with `Some(wave)`.

**`save`** is more interesting. It pattern-matches on the event type to determine
which command to send. A `WaveReleased` event means we are creating the wave in
the actor for the first time, so we send a `Create` command. A `WaveCompleted`
event triggers a `Complete` command. A `WaveCancelled` event triggers a `Cancel`
command. Each branch uses `askWithStatus`, which wraps the reply in a
`StatusReply`. If the actor rejects the command (for example, trying to complete
a wave that is already cancelled), the `Future` fails with the error message.

> **Note:** The `save` method does not blindly forward the wave and event. It
> translates domain-level intent (an event) into actor-level commands. This
> translation is the adapter's job in hexagonal architecture. The domain layer
> produces events; the Pekko adapter maps those events to the specific actor
> commands that will persist them.

**The hexagonal connection.** In Chapter 8, we saw how `AsyncWaveRepository` is
a driven port. `PekkoWaveRepository` is the adapter that wires the abstract
`findById` and `save` operations to Cluster Sharding asks. The domain layer
calls `repository.save(wave, event)` and gets a `Future[Unit]` back. It has no
idea that Pekko or sharding exist.


## Cross-Entity Queries

Single-entity operations are straightforward: get an `EntityRef` by ID, send a
command, get a reply. But what about queries that span many entities? Consider
`findByWaveId` on the task repository: "find all tasks that belong to wave X."
We cannot iterate over every task actor in the cluster and ask each one which
wave it belongs to. That would be absurdly slow and would not scale.

The solution combines two techniques: a CQRS projection table for discovery,
followed by actor fan-out for current state.

Here is `PekkoTaskRepository`, which demonstrates the pattern:

```scala
class PekkoTaskRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncTaskRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(Entity(TaskActor.EntityKey)(ctx => TaskActor(ctx.entityId)))

  def findById(id: TaskId): Future[Option[Task]] =
    sharding.entityRefFor(TaskActor.EntityKey, id.value.toString)
      .ask(TaskActor.GetState(_))

  def findByWaveId(waveId: WaveId): Future[List[Task]] =
    queryProjectionIds(
      "SELECT task_id FROM task_by_wave WHERE wave_id = $1",
      waveId.value,
      "task_id"
    ).flatMap(ids =>
      Future.sequence(ids.map(id => findById(TaskId(id)))).map(_.flatten)
    )
```

<small>*File: task/src/main/scala/neon/task/PekkoTaskRepository.scala*</small>

Let's trace the `findByWaveId` call step by step.

**Step 1: Query the projection table.** The method calls `queryProjectionIds`,
a helper from the `R2dbcProjectionQueries` trait. This executes a raw SQL query
against a read-side table called `task_by_wave`. The projection table maps wave
IDs to task IDs. It is populated by a projection handler (Chapter 12) that
consumes task events and writes the mapping whenever a task is created. The query
returns a `Future[List[UUID]]`, the list of task IDs belonging to this wave.

**Step 2: Fan out to actors.** For each task ID, `findById` sends a `GetState`
command to the corresponding `TaskActor` via Cluster Sharding. `Future.sequence`
executes all the asks in parallel. The `.flatten` filters out any `None` results
(which could occur if a task was deleted after the projection was written but
before the fan-out reached the actor).

The caller receives a `Future[List[Task]]` containing the current state of every
task belonging to the wave, fetched directly from the authoritative actors.

> **Note:** This two-step pattern is eventually consistent. There is a small
> window between when an event is persisted by the actor and when the projection
> handler updates the `task_by_wave` table. During that window, `findByWaveId`
> might return a stale list. In practice, this lag is measured in milliseconds
> and is acceptable for the read-heavy queries that use this pattern (dashboards,
> wave completion checks, reporting).


### The R2dbcProjectionQueries Trait

The `queryProjectionIds` helper lives in the `R2dbcProjectionQueries` trait in
the `common` module:

```scala
trait R2dbcProjectionQueries:
  protected def connectionFactory: ConnectionFactory
  protected given system: ActorSystem[?]
  protected given ec: ExecutionContext

  protected def queryProjectionIds(
      sql: String,
      params: List[Any],
      idColumn: String
  ): Future[List[UUID]]
```

<small>*File: common/src/main/scala/neon/common/R2dbcProjectionQueries.scala*</small>

The implementation acquires an R2DBC connection, binds the query parameters,
streams the result rows into a list of UUIDs via Pekko Streams, and closes the
connection in an `andThen` block (which runs regardless of success or failure).
Everything is non-blocking: `Source.fromPublisher` wraps R2DBC's reactive
`Publisher` into a Pekko Streams `Source`.

Any `PekkoRepository` that needs cross-entity queries mixes in this trait,
provides a `ConnectionFactory`, and calls `queryProjectionIds` with the
appropriate SQL.


## Non-Transactional saveAll

Some service operations produce multiple events for multiple entities in a
single logical step. For example, releasing a wave creates one task per order
line, each task being its own entity. The `AsyncTaskRepository` defines a
`saveAll` method for this:

```scala
/** Persists multiple entries by fanning out to individual entity actors.
  * Not transactional: individual entries may succeed or fail independently.
  */
def saveAll(entries: List[(Task, TaskEvent)]): Future[Unit]
```

<small>*File: task/src/main/scala/neon/task/AsyncTaskRepository.scala*</small>

The Scaladoc makes the contract explicit: this is not transactional. Here is the
implementation in `PekkoTaskRepository`:

```scala
def saveAll(entries: List[(Task, TaskEvent)]): Future[Unit] =
  Future.sequence(entries.map((task, event) => save(task, event))).map(_ => ())
```

<small>*File: task/src/main/scala/neon/task/PekkoTaskRepository.scala*</small>

`Future.sequence` launches all individual `save` calls in parallel. Each `save`
sends a command to its respective task actor via Cluster Sharding. If one actor
rejects its command (perhaps due to a serialization error or an invalid state
transition), that individual `Future` fails, but the others may succeed.

Why not use a distributed transaction? Because distributed transactions across
event-sourced actors are complex, introduce coordination overhead, and conflict
with the eventual consistency model that event sourcing embraces. The design
accepts that `saveAll` may partially succeed. The service layer handles this by
making operations idempotent where possible, so a retry can safely re-apply any
entries that failed on the first attempt.

> **Note:** The non-transactional nature of `saveAll` is a deliberate
> architectural choice, not an oversight. The Scaladoc makes the contract
> visible to every caller. When you see `saveAll` in a service, you know it
> fans out independently.


## R2DBC Persistence Configuration

The event-sourced actors need a journal to store events and a snapshot store for
periodic state snapshots. In production, Neon WES uses the Pekko R2DBC plugin
backed by PostgreSQL. The relevant configuration lives in `application.conf`:

```hocon
pekko.persistence {
  journal.plugin = "pekko.persistence.r2dbc.journal"
  snapshot-store.plugin = "pekko.persistence.r2dbc.snapshot"
  r2dbc {
    connection-factory = ${pekko.persistence.r2dbc.postgres}
    connection-factory {
      host = ${POSTGRES_HOST}
      port = ${POSTGRES_PORT}
      database = ${POSTGRES_DB}
      user = ${POSTGRES_USER}
      password = ${POSTGRES_PASSWORD}
    }
  }
}
```

Events are stored as rows in the `event_journal` table with the serialized
payload in a CBOR binary column. Snapshots go to the `snapshot` table, keyed
by persistence ID and sequence number. Each actor configures its retention:

```scala
EventSourcedBehavior
  .withEnforcedReplies[Command, WaveEvent, State](
    persistenceId = PersistenceId(EntityKey.name, entityId),
    emptyState = EmptyState,
    commandHandler = commandHandler(context),
    eventHandler = eventHandler
  )
  .withRetention(
    RetentionCriteria.snapshotEvery(100, 2)
  )
```

<small>*File: wave/src/main/scala/neon/wave/WaveActor.scala*</small>

`snapshotEvery(100, 2)` means: take a snapshot every 100 events, and keep the
2 most recent snapshots. On recovery, the actor loads the latest snapshot and
replays only the events that came after it, rather than replaying the entire
history from the beginning. For an aggregate that has processed thousands of
events, this dramatically reduces recovery time.

The `PersistenceId` combines the entity type name and the entity ID:
`PersistenceId("Wave", "550e8400-...")`. This string is the primary key in the
journal table, uniquely identifying the event stream for one specific wave.


## Testing Actors with EventSourcedBehaviorTestKit

We now have event-sourced actors managed by Cluster Sharding, persisting to
PostgreSQL via R2DBC. How do we test all of this without standing up a database
or a cluster? Pekko provides `EventSourcedBehaviorTestKit`, a testing harness
that runs a single actor in-process with an in-memory journal.

Here is the test setup for `WaveActorSuite`:

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

  private val waveId = WaveId()
  private val orderIds = List(OrderId(), OrderId())
  private val at = Instant.now()

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
```

<small>*File: wave/src/test/scala/neon/wave/WaveActorSuite.scala*</small>

Let's unpack this.

**The configuration stack.** The test merges two configs. The inline block sets
the actor provider to `local` (no cluster) and registers the CBOR serialization
binding. The `EventSourcedBehaviorTestKit.config` fallback provides an in-memory
journal and a local snapshot store. Together, they create a minimal environment
where the actor can persist and recover events without external dependencies.

**Serialization verification.** When `withVerifyEvents(true)` is set, every
persisted event is serialized to CBOR bytes and deserialized back. If the
round-trip produces a different object, the test fails. The same applies to
state via `withVerifyState(true)`. This catches serialization bugs at test time
rather than in production, where they would corrupt the journal.

> **Note:** Serialization verification is one of the most valuable testing
> features. A common mistake is adding a new field to an event class but
> forgetting to annotate it for Jackson. Without verification, the field
> silently drops during serialization, and the bug only surfaces when the
> actor recovers from the journal weeks later.

**The test kit instance.** `EventSourcedBehaviorTestKit` is parameterized with
the actor's three types: `Command`, `WaveEvent`, and `State`. It wraps a single
actor instance and provides methods for sending commands and inspecting results.

Now let's look at the tests:

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
        esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
          WaveActor.Create(planned, event, _)
        )
      assert(result.event == event)
      assert(
        result
          .stateOfType[WaveActor.ActiveState]
          .wave
          .isInstanceOf[Wave.Released]
      )
```

<small>*File: wave/src/test/scala/neon/wave/WaveActorSuite.scala*</small>

`runCommand` sends a command and returns a `CommandResult` containing the reply,
the persisted events, and the resulting state. Unlike a normal `ask` (which only
gives the reply), `CommandResult` lets us assert that the correct event was
persisted (`result.event == event`) and that the state transitioned to the
expected type (`ActiveState` containing a `Wave.Released`).


### Testing Command Rejection

Not all commands should succeed. The suite verifies that invalid commands are
rejected:

```scala
it("rejects Complete in EmptyState"):
  val result =
    esTestKit
      .runCommand[StatusReply[WaveActor.CompleteResponse]](
        WaveActor.Complete(at, _)
      )
  assert(result.reply.isError)
  assert(result.hasNoEvents)
```

<small>*File: wave/src/test/scala/neon/wave/WaveActorSuite.scala*</small>

Sending `Complete` to an actor in `EmptyState` (no wave created yet) must fail.
The test verifies two things: the reply is an error, and no events were
persisted. The second assertion is critical. A rejected command must never write
to the journal. If it did, the actor would recover into an inconsistent state.


### Testing Recovery

The most important test for an event-sourced actor verifies that recovery works:

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

<small>*File: wave/src/test/scala/neon/wave/WaveActorSuite.scala*</small>

This test creates a wave, then calls `esTestKit.restart()`. The restart stops
the actor and starts a fresh instance, which reconstructs its state by replaying
events from the in-memory journal. After the restart, `GetState` should return
the same `Wave.Released` that existed before. If the event handler has a bug
(for example, it does not handle `WaveReleased`), the actor starts in
`EmptyState` and `GetState` returns `None`.

This pattern verifies the entire event sourcing lifecycle: command handling
produces correct events, persistence writes them to the journal (verified by
serialization), and replay reconstructs the correct state on recovery. If any
step is broken, the recovery test catches it.

> **Note:** Every event-sourced actor in Neon WES has a recovery test. It is
> the single most important test for event-sourced systems. An actor that
> cannot recover from its own journal is fundamentally broken, no matter how
> correctly it handles commands on the first run.


### The beforeEach Reset

One subtle detail: the `beforeEach` method calls `esTestKit.clear()`, which
resets the in-memory journal and restarts the actor before each test. Without
this, events from one test would leak into the next. Each test starts with a
clean slate: an empty journal and an actor in `EmptyState`.


## What Comes Next

Cluster Sharding distributes our write model across nodes. Each entity lives
on exactly one node at a time, processes commands sequentially, and persists
events to a durable journal. The `PekkoRepository` pattern adapts this
infrastructure to the domain's port traits, keeping the domain layer clean of
any Pekko dependencies.

But read-heavy queries, like loading a dashboard of all active waves or listing
every task in a specific state, cannot fan out to thousands of actors. The
cross-entity query pattern we saw in this chapter already hints at the solution:
projection tables. In the next chapter, we will build the read side with CQRS
projections, consuming events from the journal and materializing them into
denormalized PostgreSQL tables optimized for the queries our application needs.
