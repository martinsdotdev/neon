# The Actor Model with Pekko

In Part II we built a complete domain layer that works beautifully in memory.
Pure typestate transitions, immutable events, stateless policies, orchestrating
services, and abstract repository ports: everything compiles, tests pass, and
the design is clean. But production systems need persistence, concurrency
control, and distributed execution. In this chapter, we will see how Pekko's
actor model brings all three.

## Why Actors?

Before we look at code, let's understand why Neon WES uses the actor model in
the first place. There are four properties that make actors a natural fit for
event-sourced domain aggregates.

**One actor per entity instance.** Each wave, task, or transport order gets its
own actor. The actor processes messages sequentially, one at a time. This gives
us a natural concurrency boundary with no locking, no synchronized blocks, no
compare-and-swap loops. Two commands arriving simultaneously for the same wave
will be processed in order, guaranteed by the actor's mailbox.

**Event sourcing built into the framework.** Pekko Persistence provides
`EventSourcedBehavior`, a behavior that persists events to a journal and
replays them on recovery. We do not need to build our own event store or replay
mechanism. The framework handles journal writes, snapshot management, and
recovery automatically.

**Cluster sharding distributes actors across nodes.** In a production cluster,
Pekko Cluster Sharding ensures that exactly one actor instance exists for each
entity ID, regardless of how many nodes are running. We will explore sharding
in detail in Chapter 11. For now, the key point is that each actor is
addressable by its entity ID from any node in the cluster.

**The actor is a thin shell around the domain model.** This is the most
important property. The actor does not contain business logic. It receives
commands, delegates to the domain aggregate's typestate transition methods, and
persists the resulting events. The pure domain model we built in Part II
remains the source of truth for all business rules. The actor simply provides
the infrastructure to make it persistent and concurrent.

@:callout(info)

If you have worked with Akka before, Pekko will feel familiar.
Apache Pekko is a community fork of Akka, created after Akka changed its
license. The API is nearly identical; the package names changed from
`akka.*` to `org.apache.pekko.*`.

@:@

## Anatomy of an Event-Sourced Actor

Let's open `WaveActor.scala` and walk through its structure. This is the
actor that manages the lifecycle of a single `Wave` aggregate.

```scala
object WaveActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Wave")
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

The `EntityKey` is a cluster sharding identifier. It tells Pekko that actors
of this type are known as `"Wave"` entities. When we send a command to wave
`abc-123`, the cluster sharding infrastructure uses this key to locate (or
create) the correct actor. We will see how this works in Chapter 11.

The core of the actor is the `apply` method, which constructs the behavior:

```scala
def apply(entityId: String): Behavior[Command] =
  Behaviors.withMdc[Command](
    Map("entityType" -> "Wave", "entityId" -> entityId)
  ):
    Behaviors.setup: context =>
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

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

There is a lot happening in these few lines. Let's unpack it piece by piece.

`EventSourcedBehavior.withEnforcedReplies` takes three type parameters:

1. **`Command`**: the message type the actor accepts. Every message sent to
   this actor must be a subtype of `Command`.
2. **`WaveEvent`**: the event type the actor persists. These are the same
   domain events from Chapter 5.
3. **`State`**: the actor's internal state, reconstructed from events on
   recovery.

The method also takes four value parameters:

- **`persistenceId`**: a unique identifier that links the actor to its journal
  entries. It combines the entity type name (`"Wave"`) with the entity ID
  (`entityId`). Every event this actor persists is stored under this
  persistence ID.
- **`emptyState`**: the state before any events have been applied. For a fresh
  entity that has never received a command, the state is `EmptyState`.
- **`commandHandler`**: a function `(State, Command) => ReplyEffect` that
  decides what to do with each incoming command.
- **`eventHandler`**: a function `(State, WaveEvent) => State` that
  reconstructs state from persisted events during recovery.

These two functions are the heart of the actor. Everything else is
configuration.

## Commands

Every actor needs a protocol: the set of messages it can receive. In Pekko,
we define this as a sealed trait.

```scala
sealed trait Command extends CborSerializable

case class Create(
    planned: Wave.Planned,
    event: WaveEvent.WaveReleased,
    replyTo: ActorRef[StatusReply[Done]]
) extends Command

case class Release(
    at: Instant,
    replyTo: ActorRef[StatusReply[ReleaseResponse]]
) extends Command

case class Complete(
    at: Instant,
    replyTo: ActorRef[StatusReply[CompleteResponse]]
) extends Command

case class Cancel(
    at: Instant,
    replyTo: ActorRef[StatusReply[CancelResponse]]
) extends Command

case class GetState(replyTo: ActorRef[Option[Wave]]) extends Command
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

Each command extends `CborSerializable`, the marker trait from `common` that
tells Pekko to serialize these messages with Jackson CBOR. In a cluster, commands
may travel across the network between nodes, so they must be serializable.

Notice that every command carries a `replyTo` field. This is how the caller
receives a response. When the actor finishes processing a command, it sends the
result back through this `ActorRef`. The type parameter on `ActorRef` specifies
exactly what the caller will receive: `StatusReply[Done]` for `Create`,
`StatusReply[ReleaseResponse]` for `Release`, and so on.

The command set mirrors the domain aggregate's transitions. `Create` initializes
the entity, `Release` moves it from `Planned` to `Released`, `Complete` moves
it from `Released` to `Completed`, `Cancel` terminates it from any non-terminal
state, and `GetState` is a read-only query.

@:callout(info)

We used `withEnforcedReplies` rather than the plain
`EventSourcedBehavior` constructor. This variant requires every code path in
the command handler to produce a reply. If you forget to reply in some branch,
the code will not compile. This eliminates a common class of bugs where a
caller sends a command and waits forever for a response that never comes.

@:@

## State: EmptyState and ActiveState

The actor's internal state is a simple two-case sealed trait:

```scala
sealed trait State extends CborSerializable
case object EmptyState extends State
case class ActiveState(wave: Wave) extends State
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

**`EmptyState`** represents an actor that has not yet received its first event.
When Pekko creates a new actor for an entity ID that has no journal history,
the state starts here.

**`ActiveState(wave: Wave)`** wraps the domain aggregate. The `wave` field
holds whichever typestate the aggregate is currently in: `Wave.Planned`,
`Wave.Released`, `Wave.Completed`, or `Wave.Cancelled`. The actor does not
need separate state classes for each domain state because the sealed trait
hierarchy inside `Wave` already encodes that information.

Both cases extend `CborSerializable` because Pekko may serialize the state
when taking snapshots. We will discuss snapshots later in this chapter.

## The Command Handler

The command handler is where infrastructure meets domain logic. Let's walk
through the key cases.

```scala
private def commandHandler(
    context: ActorContext[Command]
): (State, Command) => ReplyEffect[WaveEvent, State] =
  (state, command) =>
    context.log.debug(
      "Received {} in state {}",
      command.getClass.getSimpleName,
      state.getClass.getSimpleName
    )
    (state, command) match

      case (EmptyState, Create(planned, event, replyTo)) =>
        Effect
          .persist(event)
          .thenReply(replyTo)(_ => StatusReply.ack())

      case (ActiveState(planned: Wave.Planned), Release(at, replyTo)) =>
        val (released, event) = planned.release(at)
        Effect
          .persist(event)
          .thenReply(replyTo)(_ =>
            StatusReply.success(ReleaseResponse(released, event)))
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

**The `Create` case.** When the actor is in `EmptyState` and receives a
`Create` command, it persists the provided `WaveReleased` event and replies
with an acknowledgment. The caller (the `PekkoWaveRepository`) has already
constructed the domain objects; the actor's job is simply to persist the event.

**The `Release` case.** This is where delegation to the domain model is most
visible. The command handler pattern-matches on `ActiveState(planned:
Wave.Planned)`, extracting the `Planned` typestate. It then calls the domain
method `planned.release(at)`, which returns a `(Wave.Released,
WaveEvent.WaveReleased)` tuple. The actor persists the event and replies with
both the new state and the event.

The key insight here is what the command handler does _not_ do. It does not
validate whether a release is allowed. It does not check preconditions. It does
not contain business logic. All of that lives in the domain model's typestate
transition methods. The pattern match on `Wave.Planned` ensures that release is
only attempted on a planned wave. If the wave is in any other state, this case
simply will not match.

The `Complete` and `Cancel` cases follow the same pattern:

```scala
      case (ActiveState(released: Wave.Released), Complete(at, replyTo)) =>
        val (completed, event) = released.complete(at)
        Effect
          .persist(event)
          .thenReply(replyTo)(_ =>
            StatusReply.success(CompleteResponse(completed, event)))

      case (ActiveState(planned: Wave.Planned), Cancel(at, replyTo)) =>
        val (cancelled, event) = planned.cancel(at)
        Effect
          .persist(event)
          .thenReply(replyTo)(_ =>
            StatusReply.success(CancelResponse(cancelled, event)))

      case (ActiveState(released: Wave.Released), Cancel(at, replyTo)) =>
        val (cancelled, event) = released.cancel(at)
        Effect
          .persist(event)
          .thenReply(replyTo)(_ =>
            StatusReply.success(CancelResponse(cancelled, event)))
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

Notice that `Cancel` has two cases: one for `Wave.Planned` and one for
`Wave.Released`. This mirrors the domain model, where both states offer a
`cancel` method. A `Completed` or `Cancelled` wave cannot be cancelled,
and there is no case for those states. The catch-all handles them.

The `GetState` query and the catch-all error case complete the handler:

```scala
      case (_, GetState(replyTo)) =>
        val wave = state match
          case EmptyState        => None
          case ActiveState(wave) => Some(wave)
        Effect.reply(replyTo)(wave)

      case (_, cmd) =>
        val msg =
          s"Invalid command ${cmd.getClass.getSimpleName} " +
            s"in state ${state.getClass.getSimpleName}"
        context.log.warn(msg)
        cmd match
          case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
          case c: Release  => Effect.reply(c.replyTo)(StatusReply.error(msg))
          case c: Complete => Effect.reply(c.replyTo)(StatusReply.error(msg))
          case c: Cancel   => Effect.reply(c.replyTo)(StatusReply.error(msg))
          case c: GetState => Effect.reply(c.replyTo)(None)
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

`GetState` is a pure read. It does not persist any event; it simply replies
with the current aggregate wrapped in an `Option`. This is useful for the
`PekkoWaveRepository` when it needs to fetch an entity's current state.

The catch-all `(_, cmd)` matches any command that does not have a valid handler
for the current state. It logs a warning and replies with an error. Because we
used `withEnforcedReplies`, the compiler requires that every branch produces a
reply. The catch-all must extract `replyTo` from each possible command type to
satisfy this requirement.

## The Event Handler

The event handler reconstructs state from persisted events. It runs during
recovery (when the actor restarts and replays its journal) and after each
successful `Effect.persist` call.

```scala
private val eventHandler: (State, WaveEvent) => State =
  (state, event) =>
    event match
      case e: WaveEvent.WaveReleased =>
        ActiveState(Wave.Released(e.waveId, e.orderGrouping, e.orderIds))
      case e: WaveEvent.WaveCompleted =>
        state match
          case ActiveState(w) =>
            ActiveState(Wave.Completed(w.id, w.orderGrouping))
          case _ => state
      case e: WaveEvent.WaveCancelled =>
        state match
          case ActiveState(w) =>
            ActiveState(Wave.Cancelled(w.id, w.orderGrouping))
          case _ => state
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

Three rules govern event handlers:

1. **They must be deterministic.** Given the same state and event, the handler
   must always produce the same new state. No random values, no timestamps,
   no external lookups.

2. **They must be side-effect-free.** No logging, no database writes, no
   message sends. The event handler may be called during recovery, where
   side effects would be replayed and duplicated.

3. **They must not fail.** Events are facts that have already happened. The
   event handler does not validate; it simply applies the fact to produce
   the next state.

Notice how the `WaveReleased` case constructs a `Wave.Released` directly from
the event's fields, without going through the `Planned.release` transition
method. The command handler calls domain methods with validation. The event
handler bypasses validation because the event has already been accepted and
persisted. It is a record of something that definitively occurred.

The `WaveCompleted` and `WaveCancelled` cases need the previous state to
retrieve the `id` and `orderGrouping` fields, since those are not redundantly
stored in every event. They pattern-match on `ActiveState` to extract the
current wave, then construct the new typestate. The fallback `case _ => state`
is a safety net that should never be reached in practice.

## Responses

Each command type has a corresponding response that bundles the new state with
the event:

```scala
case class ReleaseResponse(
    released: Wave.Released,
    event: WaveEvent.WaveReleased
) extends CborSerializable

case class CompleteResponse(
    completed: Wave.Completed,
    event: WaveEvent.WaveCompleted
) extends CborSerializable

case class CancelResponse(
    cancelled: Wave.Cancelled,
    event: WaveEvent.WaveCancelled
) extends CborSerializable
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

Why does the response include both the state and the event? The caller (the
`PekkoWaveRepository`, which we will build in Chapter 11) often needs both
pieces. The state tells the caller what the entity looks like now. The event
tells the caller what happened, which is useful for triggering downstream
processes like projections or service cascades.

`StatusReply` is a Pekko utility that wraps responses in either a `Success` or
an `Error`. The `Create` command uses `StatusReply.ack()`, a convenience for
commands that do not need to return data beyond "it worked." The other commands
use `StatusReply.success(response)` to deliver the full response object.

## Retention and Snapshots

As events accumulate in the journal, recovery time grows. If a wave actor has
processed 10,000 events, replaying all of them on restart would be slow. Pekko
solves this with snapshots.

```scala
.withRetention(
  RetentionCriteria.snapshotEvery(100, 2)
)
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

This configuration tells Pekko to save a snapshot of the actor's state every
100 events and to keep the 2 most recent snapshots. On recovery, Pekko loads
the latest snapshot and replays only the events that occurred after it. Instead
of replaying 10,000 events, the actor might load a snapshot at event 9,900 and
replay just 100 events.

Snapshots are serialized using the same Jackson CBOR mechanism as events and
commands. This is where the `@JsonTypeInfo` annotation on the `Wave` sealed
trait becomes critical:

```scala
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait Wave:
  def id: WaveId
  def orderGrouping: OrderGrouping
```

<small>_File: wave/src/main/scala/neon/wave/Wave.scala_</small>

Without this annotation, Jackson would not know how to deserialize a snapshot
containing a `Wave.Released` versus a `Wave.Planned`. The `ActiveState` wrapper
holds a `Wave`, which is the sealed trait. When Jackson reads the snapshot
bytes, it needs the class discriminator to determine which concrete subtype to
instantiate. The `@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)` annotation tells
Jackson to include the fully qualified class name in the serialized form, so
deserialization can reconstruct the correct type.

@:callout(info)

Forgetting `@JsonTypeInfo` on a polymorphic snapshot type is a
common source of production bugs. The actor will persist events and snapshots
without error, but when it tries to recover from a snapshot, deserialization
fails with a cryptic Jackson error. Always annotate sealed traits that appear
in actor state.

@:@

## Structured Logging with MDC

The outermost wrapper in the `apply` method sets up MDC (Mapped Diagnostic
Context) fields for structured logging:

```scala
Behaviors.withMdc[Command](
  Map("entityType" -> "Wave", "entityId" -> entityId)
):
  Behaviors.setup: context =>
    // ...
```

<small>_File: wave/src/main/scala/neon/wave/WaveActor.scala_</small>

MDC is a logging concept where key-value pairs are attached to every log
statement within a scope. By wrapping the entire behavior in `withMdc`, every
log message produced by this actor, whether from the command handler, event
handler, or Pekko internals, will include `entityType=Wave` and
`entityId=<the-id>`.

In production, this is invaluable for debugging. When you see a log line like:

```
WARN [entityType=Wave, entityId=abc-123] Invalid command Cancel in state Completed
```

You immediately know which entity had the problem, without parsing the message
text. Structured logging fields can be indexed and searched in tools like
Elasticsearch or Grafana Loki, making it easy to filter logs for a specific
entity or entity type.

## Reusing Domain Events Directly

You may have noticed something unusual about our actor. If you look at the
type parameter list again:

```scala
EventSourcedBehavior
  .withEnforcedReplies[Command, WaveEvent, State]
```

The second type parameter is `WaveEvent`, the domain event type from Chapter 5.
Many Pekko examples define a separate "actor event" wrapper type (sometimes
called `ActorEvent` or `PersistenceEvent`) that wraps domain events with
additional metadata. We chose a simpler approach: the actor persists domain
events directly.

This decision has a practical benefit. The events stored in the journal are
the same types consumed by CQRS projections (Chapter 12) and read by the
event handler. There is no translation layer, no adapter between "actor events"
and "domain events." One event type serves all purposes.

The trade-off is that domain events must extend `CborSerializable`, which they
already do:

```scala
sealed trait WaveEvent extends CborSerializable:
  def waveId: WaveId
  def orderGrouping: OrderGrouping
  def occurredAt: Instant
```

<small>_File: wave/src/main/scala/neon/wave/WaveEvent.scala_</small>

Since we need events to be serializable for the journal anyway, requiring
`CborSerializable` on domain events is not an additional cost. The marker
trait carries no methods or fields; it simply tells Pekko's serialization
configuration to use Jackson CBOR for these types.

## Architecture Note: Actors as Deciders

Let's step back and look at the big picture. Jérémie Chassaing's Decider
pattern describes event-sourced systems in terms of three components:

- **`decide`**: given the current state and a command, produce a list of
  events (or reject the command).
- **`evolve`**: given the current state and an event, produce the next state.
- **`initialState`**: the state before anything has happened.

Our `WaveActor` implements exactly this interface:

| Decider concept | WaveActor implementation                          |
| --------------- | ------------------------------------------------- |
| `decide`        | `commandHandler: (State, Command) => ReplyEffect` |
| `evolve`        | `eventHandler: (State, WaveEvent) => State`       |
| `initialState`  | `EmptyState`                                      |

The command handler is `decide`. It examines the current state and the incoming
command, then either persists events (accepting the command) or replies with an
error (rejecting it). The event handler is `evolve`. It takes the current state
and an event, then produces the next state. `EmptyState` is the initial state.

What makes this architecture powerful is the layering. The Pekko actor provides
the Decider interface to the infrastructure (persistence, recovery, clustering).
Inside the command handler, the actual decision logic delegates to the domain
aggregate's typestate transition methods. The `planned.release(at)` call in the
command handler is a call into the pure domain model from Part II. The actor
adds persistence and concurrency around it, but the business rules live where
they always did.

This layering means:

- **Domain logic is testable without actors.** We tested `Wave.Planned.release`
  in Chapter 4 with plain unit tests. No actor system, no journal, no
  serialization.
- **Actor tests verify the infrastructure shell.** We can test that commands
  produce the right events, that recovery works, and that invalid commands
  are rejected, without worrying about business rule correctness (that is
  already covered by domain tests).
- **Changes to business rules do not require actor changes.** If the `release`
  method gains a new precondition, we update the domain model and its unit
  tests. The actor code remains unchanged.

## The Pattern Across All Actors

`WaveActor` is not unique. Every event-sourced aggregate in Neon WES follows
the same structure. Here is the pattern:

```
<module>/
  <Aggregate>.scala           # Domain: sealed trait + typestates
  <Aggregate>Event.scala      # Domain: sealed trait of events
  <Aggregate>Actor.scala      # Infrastructure: event-sourced actor
```

Each actor defines:

1. An `EntityKey` for cluster sharding
2. A `Command` sealed trait with one case class per operation
3. Response case classes bundling state and event
4. A `State` sealed trait with `EmptyState` and `ActiveState`
5. An `apply` method constructing `EventSourcedBehavior.withEnforcedReplies`
6. A command handler that delegates to domain methods
7. An event handler that reconstructs state from events
8. Retention configuration for snapshots

The specifics vary (a `TaskActor` has more commands than a `WaveActor`), but
the structure is identical. Once you understand one actor, you understand them
all.

@:callout(info)

This structural consistency is deliberate. Learn the pattern once
and you can navigate any actor in the system. When adding a new
aggregate module, the actor file is mostly mechanical: define commands, wire
up domain transitions in the command handler, mirror them in the event
handler. The creative work happens in the domain model.

@:@

## What We Covered

In this chapter, we bridged the gap between pure domain logic and persistent
infrastructure. We learned that:

- An event-sourced actor manages one entity instance, processing commands
  sequentially and persisting events to a journal.
- `EventSourcedBehavior.withEnforcedReplies` takes three type parameters
  (`Command`, `Event`, `State`) and two core functions (`commandHandler`,
  `eventHandler`).
- Commands are sealed traits with `replyTo` fields. `withEnforcedReplies`
  guarantees at compile time that every code path produces a reply.
- The command handler delegates to domain typestate methods. It does not
  duplicate business logic.
- The event handler reconstructs state during recovery. It must be
  deterministic, side-effect-free, and infallible.
- Snapshots reduce recovery time. `@JsonTypeInfo` on sealed traits enables
  polymorphic deserialization of snapshots.
- MDC fields on the actor behavior attach entity metadata to every log
  message.
- The actor implements the Decider pattern: `decide` (command handler),
  `evolve` (event handler), `initialState` (EmptyState).

## What Comes Next

Now we know how a single actor manages one aggregate's lifecycle. But how do
we route messages to the right actor, across potentially many cluster nodes?
And how does the rest of the application talk to actors without knowing their
physical location? In the next chapter, we will explore cluster sharding and
the `PekkoRepository` adapter that bridges the repository port traits from
Chapter 8 to the actor infrastructure we built here.
