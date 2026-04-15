# Events: The Source of Truth

In Chapter 4, we saw that every state transition method returns a tuple of
`(NewState, Event)`. We focused on the new state: how typestate encoding makes
illegal transitions impossible at compile time. Now let's turn our attention to
the other half of that tuple. The event is an immutable record of what happened,
and in an event-sourced system, it is the single source of truth.

## What Is a Domain Event?

A _domain event_ is an immutable record of something that happened in the
domain. Not something that _should_ happen, not something that _might_ happen,
but something that _already did_. When we say `WaveReleased`, we mean the wave
has been released. It is a fact, written in past tense, permanently recorded.

This is a deliberate naming convention. Every event type in Neon WES uses past
tense: `WaveReleased`, `TaskAllocated`, `ConsolidationGroupPicked`,
`TransportOrderConfirmed`. The tense tells you everything about the semantics.
An event is not a request or an intention. It is a historical record, and like
all historical records, it cannot be changed after the fact.

Why does this matter? Because in an event-sourced system, events are the data
you keep forever. The current state of any aggregate can be reconstructed by
replaying its events from the beginning. If you lose the state, you can rebuild
it. If you lose the events, the state is meaningless. Events are the source of
truth; state is derived.

@:callout(info)

We will see the full event-sourcing infrastructure in Chapters 10
and 11. For now, we are focusing on the events themselves: how they are
structured, what they carry, and why they are designed the way they are.

@:@

## Events vs Commands

Before we dive into event design, let's draw a clear line between events and
commands. These two concepts are easy to confuse, and confusing them leads to
architectural mistakes.

A **command** is a request: "Release this wave." It can be rejected. The wave
might already be released. The task might already be cancelled. Commands
express intent; they do not guarantee outcomes.

An **event** is a fact: "This wave was released." It records something that has
already happened. You cannot reject an event any more than you can reject
yesterday's weather.

Consider the flow:

1. Someone issues a command: "Release wave W-001."
2. The system validates: is the wave in the `Planned` state? Does it have orders?
3. If valid, the transition executes and produces an event: `WaveReleased`.
4. The event is persisted to the journal. It is now permanent.

In Neon WES, commands will appear formally in Chapter 10 when we introduce
Pekko actors. For now, the relevant point is that events are produced by
aggregate transition methods. When you call `planned.release(at)`, it returns a
`WaveReleased` event. That event is the proof that the transition happened.

## Event Design in Neon WES

Let's look at how Neon WES structures its events, starting with the simplest
example and building toward more complex ones.

### Wave events: the minimal case

```scala
sealed trait WaveEvent extends CborSerializable:
  def waveId: WaveId
  def orderGrouping: OrderGrouping
  def occurredAt: Instant

object WaveEvent:

  case class WaveReleased(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      orderIds: List[OrderId],
      occurredAt: Instant
  ) extends WaveEvent

  case class WaveCompleted(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      occurredAt: Instant
  ) extends WaveEvent

  case class WaveCancelled(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      occurredAt: Instant
  ) extends WaveEvent
```

<small>_File: wave/src/main/scala/neon/wave/WaveEvent.scala_</small>

Three things to notice here.

**The sealed trait defines shared fields.** Every wave event carries `waveId`,
`orderGrouping`, and `occurredAt`. These fields appear on the trait itself, so
any code that handles a `WaveEvent` (regardless of which specific event type)
can always access the wave's identity, its grouping strategy, and when the
event happened.

**One event per transition.** The wave state machine has three transitions
(release, complete, cancel), and there are exactly three event types. This is a
one-to-one mapping. There is no generic "WaveUpdated" event that tries to cover
multiple transitions. Each event type corresponds to exactly one thing that can
happen to a wave.

**Events extend `CborSerializable`.** This is the marker trait from Chapter 3.
It tells the serialization layer that these events will be stored in the journal
using CBOR (Concise Binary Object Representation). We will cover the
serialization mechanics in Chapter 17; for now, just know that this marker is
what makes events persistable.

### Task events: the richer case

Task events carry significantly more data:

```scala
sealed trait TaskEvent extends CborSerializable:
  def taskId: TaskId
  def taskType: TaskType
  def occurredAt: Instant

object TaskEvent:

  case class TaskCreated(
      taskId: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      requestedQuantity: Int,
      occurredAt: Instant,
      stockPositionId: Option[StockPositionId] = None
  ) extends TaskEvent

  case class TaskAllocated(
      taskId: TaskId,
      taskType: TaskType,
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
      occurredAt: Instant
  ) extends TaskEvent

  case class TaskAssigned(
      taskId: TaskId,
      taskType: TaskType,
      userId: UserId,
      occurredAt: Instant
  ) extends TaskEvent

  case class TaskCompleted(
      taskId: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
      requestedQuantity: Int,
      actualQuantity: Int,
      assignedTo: UserId,
      occurredAt: Instant
  ) extends TaskEvent

  case class TaskCancelled(
      taskId: TaskId,
      taskType: TaskType,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      sourceLocationId: Option[LocationId],
      destinationLocationId: Option[LocationId],
      assignedTo: Option[UserId],
      occurredAt: Instant
  ) extends TaskEvent
```

<small>_File: task/src/main/scala/neon/task/TaskEvent.scala_</small>

Compare `TaskCompleted` with `WaveCompleted`. The wave completion event carries
three fields. The task completion event carries thirteen. This difference is not
accidental; it reflects the different needs of downstream consumers.

Look at `TaskCompleted` closely. It carries `requestedQuantity` _and_
`actualQuantity`. When a picker is sent to pick 10 units of a SKU but finds
only 7 on the shelf, the task completes with `requestedQuantity = 10` and
`actualQuantity = 7`. This is a _shortpick_, and the downstream shortpick
policy needs both numbers to decide whether to create a replacement task for
the remaining 3. Without both quantities in the event, the policy would need to
look up the original task state, adding coupling and complexity.

Now look at `TaskCancelled`. Its location and user fields are all `Option`
types: `sourceLocationId: Option[LocationId]`, `assignedTo: Option[UserId]`.
Why? Because cancellation can happen from three different states. A `Planned`
task has no locations yet (so both are `None`). An `Allocated` task has
locations but no user (so `assignedTo` is `None`). An `Assigned` task has all
three (so they are all `Some`). The event must accommodate all entry points.

Each `cancel()` method on the aggregate fills in the optional fields based on
what was known at the time:

```scala
// cancel() on Planned: no locations, no user
TaskEvent.TaskCancelled(id, taskType, waveId, parentTaskId,
  handlingUnitId, None, None, None, at)

// cancel() on Allocated: locations known, no user
TaskEvent.TaskCancelled(id, taskType, waveId, parentTaskId,
  handlingUnitId, Some(sourceLocationId),
  Some(destinationLocationId), None, at)

// cancel() on Assigned: everything known
TaskEvent.TaskCancelled(id, taskType, waveId, parentTaskId,
  handlingUnitId, Some(sourceLocationId),
  Some(destinationLocationId), Some(assignedTo), at)
```

## The Transition Tuple, Revisited

In Chapter 4, we introduced the `(NewState, Event)` return type as a
structural pattern. Now that we understand what events are, let's look at that
pattern again with fresh eyes.

```scala
def release(at: Instant): (Released, WaveEvent.WaveReleased) =
  val released = Released(id, orderGrouping, orderIds)
  val event = WaveEvent.WaveReleased(id, orderGrouping, orderIds, at)
  (released, event)
```

<small>_File: wave/src/main/scala/neon/wave/Wave.scala_</small>

The method constructs both the new state and the event from the same inputs,
in the same method call. There is no way to produce the state without the
event, and no way to produce the event without the state.

Why does this matter? If these were separate operations, you might update the
state but forget to emit the event, or emit an event whose fields do not match
the state. By bundling them together, the transition method guarantees
consistency. The caller receives both, and both reflect the same transition.

The caller then has two things to do with the tuple:

1. Use the **new state** as the current state of the aggregate (for the write
   model).
2. Use the **event** for persistence (writing to the journal) and for
   downstream processing (projections, policies, other services).

Neither piece can be forgotten because both are in the return value. The
compiler will warn you about unused values if you destructure the tuple and
ignore one half.

## What Events Carry

The amount of data in an event is a design decision. Too little, and downstream
consumers need to look up extra information. Too much, and events become
bloated and harder to evolve. Neon WES follows a practical guideline: **events
should carry enough data for consumers to do their work without additional
lookups.**

Let's see how this plays out across the system.

### Minimal events

`WaveCompleted` carries only three fields: `waveId`, `orderGrouping`, and
`occurredAt`. This is enough because the consumers of wave completion (the wave
completion check in the core service) already have access to the wave state
through the repository. The event's job is to signal that completion happened,
not to replicate the entire wave.

`TaskAllocated` is similarly lean: `taskId`, `taskType`, `sourceLocationId`,
`destinationLocationId`, and `occurredAt`. The event records the new
information that this transition introduced (the two locations) and nothing
more.

### Rich events

`TaskCompleted` is the richest event in the system. It carries the SKU, the
packaging level, the wave, the parent task, the handling unit, both locations,
both quantities, and the assigned user. Why so much?

Because `TaskCompleted` is the most consumed event in Neon WES. It triggers:

- **Shortpick detection**: needs `requestedQuantity` and `actualQuantity`
- **Transport order routing**: needs `destinationLocationId` and
  `handlingUnitId`
- **Wave completion checks**: needs `waveId`
- **Consolidation group progression**: needs `waveId` and `handlingUnitId`
- **Audit trails**: needs `assignedTo`, `skuId`, and both locations

If the event did not carry these fields, each consumer would need to look up
the completed task's full state from a repository. With a rich event, each
consumer can extract exactly the fields it needs from the event itself. This is
a deliberate trade-off: a larger event payload in exchange for decoupled,
self-sufficient consumers.

### The design principle

The dividing line is consumer independence. If an event's consumers can do
their work with just the event data, the event is carrying the right amount. If
consumers routinely need to call back into a repository after receiving an
event, the event is too lean. If an event carries fields that no consumer ever
reads, it is too rich.

@:callout(info)

This guideline is sometimes called "fat events" in event-sourcing
literature. The alternative, "thin events" that carry only an entity ID and
force consumers to look up the rest, trades smaller event payloads for tighter
coupling between consumers and the write model. Neon WES favors fat events
because warehouse operations involve many cross-aggregate reactions, and
keeping those reactions self-contained in their event handlers simplifies the
overall architecture.

@:@

## Events as the Contract Between Layers

Events do more than record history. They serve as the contract between three
distinct layers of the architecture:

1. **Aggregates produce events.** The typestate transition methods from
   Chapter 4 create events as part of every state change.

2. **Actors persist events.** The Pekko event-sourced actors (Chapter 10)
   receive commands, delegate to aggregate transition methods, and write the
   resulting events to a durable journal.

3. **Projections consume events.** The CQRS projection handlers (Chapter 12)
   read events from the journal and build read-side views: denormalized tables
   optimized for queries.

Events are the lingua franca between these layers. The aggregate does not know
about Pekko. The projection does not know about the aggregate's internal state
types. Both speak the language of events.

This makes events an API boundary. Adding a field to `TaskCompleted` means
updating the aggregate's transition method, the actor's event handler, and
every projection that consumes that event. This is a feature, not a bug: the
compiler makes the impact of changes visible. You cannot quietly change what an
event means without updating its consumers.

Here is the flow as a sequence:

```
Aggregate         Actor            Journal          Projection
    |                |                |                |
    |-- (State, Event) ->            |                |
    |                |-- persist Event ->             |
    |                |                |-- read Event -->
    |                |                |                |-- update read model
```

We will build each of these layers in later chapters. The point for now is that
events are the bridge. They are the only artifact that crosses all three
boundaries.

## Event Naming Conventions

Neon WES follows a consistent set of conventions across all event types. These
conventions make the event catalogue predictable and self-documenting.

**Always past tense.** Events describe what happened: `WaveReleased`,
`TaskAllocated`, `ConsolidationGroupPicked`, `TransportOrderConfirmed`,
`SlotReserved`. Never imperative (`ReleaseWave`) or present tense
(`WaveReleasing`).

**Prefixed with the aggregate name.** `WaveReleased`, not just `Released`.
`TaskCompleted`, not just `Completed`. When you see an event type in a log or
a projection handler, you know immediately which aggregate produced it.

**One event per transition.** Each arrow in the state diagram from Chapter 4
corresponds to exactly one event type. There are no "catch-all" events that
try to represent multiple transitions with a discriminator field.

**Every event carries `occurredAt`.** This `Instant` field is defined on the
sealed trait, so it is present on every event in the hierarchy. It records when
the transition happened, not when the event was persisted (which may be
slightly later).

**The event hierarchy mirrors the state machine.** Count the events in
`WaveEvent`: three. Count the transitions in the wave state machine: also three
(release, complete, cancel; both `Planned` and `Released` use the same
`WaveCancelled` event type). The events tell you the shape of the state machine
without looking at the aggregate code.

Let's verify this with a larger example. The consolidation group has six events:

```scala
object ConsolidationGroupEvent:
  case class ConsolidationGroupCreated(...)    extends ConsolidationGroupEvent
  case class ConsolidationGroupPicked(...)     extends ConsolidationGroupEvent
  case class ConsolidationGroupReadyForWorkstation(...)
      extends ConsolidationGroupEvent
  case class ConsolidationGroupAssigned(...)   extends ConsolidationGroupEvent
  case class ConsolidationGroupCompleted(...)  extends ConsolidationGroupEvent
  case class ConsolidationGroupCancelled(...)  extends ConsolidationGroupEvent
```

<small>_File: consolidation-group/src/main/scala/neon/consolidationgroup/ConsolidationGroupEvent.scala_</small>

Six events, six transitions: create, pick, readyForWorkstation, assign,
complete, cancel. The pattern holds. Reading the event companion object is
like reading the state diagram in code.

## Architecture Note: The Elm Architecture

If you have worked with Elm or followed its influence on frontend architectures,
the `(NewState, Event)` tuple should look familiar. In Elm, the core update
function has this signature:

```
update : Msg -> Model -> (Model, Cmd Msg)
```

It takes a message and the current model, and returns a new model plus a
description of side effects (commands). The Elm runtime handles the side
effects; the update function itself is pure. Neon's transition methods follow
the same structure:

```scala
def release(at: Instant): (Released, WaveEvent.WaveReleased)
```

The method takes its inputs, returns the new state plus a description of what
happened (the event). The aggregate performs no side effects. It does not write
to a database, send a message, or update a global variable. It computes the
new state and produces the event. The Pekko runtime (like the Elm runtime)
handles the side effects: persisting the event to the journal, delivering it
to projections, and notifying other actors.

This separation has the same benefits in both contexts. Testability: you can
test a transition method by calling it with inputs and asserting on outputs, no
mocks or test containers required. Predictability: given the same inputs, a
transition method always produces the same outputs, with no hidden state or
network calls. Composability: the runtime can batch events, reorder them, or
replay them for recovery, all without the aggregate knowing or caring.

The parallel is not exact. Elm's `Cmd` describes effects that _should_ happen
in the future (HTTP requests, random number generation). Neon's events describe
things that _already_ happened. But the architectural insight is the same:
separate the computation of new state from the execution of side effects, and
let the runtime handle the messy parts.

## What Comes Next

Events record what happened. But who decides what _should_ happen? When a task
is completed with fewer items than requested, who creates the replacement task?
When a handling unit arrives at a consolidation buffer, who updates the
consolidation group? In the next chapter, we will meet policies: pure,
stateless functions that encode business rules as pattern matching over events
and state.
