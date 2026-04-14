# CQRS and Projections

In the previous chapters we built event-sourced actors that persist domain
events to an R2DBC journal. Those events are the single source of truth for
every aggregate. But there is a problem: querying across aggregates is
painful. If you want to list all tasks for a given wave, you would have to
fan out `GetState` commands to every task entity in the cluster and filter
the results. That is slow, wasteful, and does not scale.

CQRS (Command Query Responsibility Segregation) solves this by splitting our
system into two sides. The *write side* is the event-sourced actors we built
in Chapters 10 and 11. The *read side* is a set of projections that consume
those same events and populate query-optimized PostgreSQL tables. Each side
can evolve independently: the write model is optimized for consistency and
state machine correctness, while the read model is optimized for fast queries
and flexible filtering.

In this chapter we will explore how Neon WES wires up projections with
`ShardedDaemonProcess`, how individual projection handlers consume events and
upsert into read-side tables, and how the `LoggingProjectionHandler` base
class provides structured observability for every projection in the system.


## Why CQRS?

Let's start with the fundamental tension that CQRS resolves.

Our event-sourced actors store their state as a sequence of events in the
R2DBC journal. They are excellent at one thing: receiving a command, validating
it against the current state, and persisting the resulting event. Each actor
owns exactly one entity instance (one wave, one task, one workstation), and
Pekko Cluster Sharding guarantees that only one actor per entity exists in the
cluster at any time.

This design is perfect for writes. But reads are a different story. Consider
these common queries:

- "Show me all tasks for wave W-42."
- "List every workstation that is currently Idle."
- "How many consolidation groups are in state ReadyForWorkstation?"
- "What is the current stock position for SKU-A in warehouse area 1?"

None of these queries map naturally to a single entity. To answer "all tasks
for wave W-42," we would need to know which task IDs belong to that wave (we
do not have a global index), then ask each task actor for its state, then
assemble the results. That is an N+1 pattern at the cluster level.

CQRS eliminates this problem by maintaining dedicated read-side tables that
are updated asynchronously from the event stream. The `task_by_wave` table,
for example, is indexed by `wave_id` and stores the current state of every
task. A simple `SELECT * FROM task_by_wave WHERE wave_id = ?` returns
everything we need in one query.

> **Note:** The read side is *eventually consistent* with the write side.
> After a command succeeds and an event is persisted, there is a small delay
> (typically milliseconds) before the projection handler processes that event
> and updates the read-side table. For warehouse operations, this latency is
> negligible. The critical guarantee is that the write side is always
> consistent: the actor will never accept an invalid command.

The trade-off is clear. We maintain two representations of the same data.
The write side is the authoritative event log; the read side is a derived
view. If a read-side table ever becomes corrupted or needs a schema change,
we can rebuild it by replaying events from the journal. Nothing is lost.


## Projection Architecture

Neon WES initializes all its projections in a single bootstrap object. Let's
look at how the pieces fit together.

### The Building Blocks

Three Pekko components collaborate to make projections work:

1. **EventSourcedProvider**: reads events from the R2DBC journal, partitioned
   by *slice ranges*. Slices are a sharding concept that divides entity IDs
   into numbered buckets (0 to 1023). Each projection instance is responsible
   for a contiguous range of slices.

2. **R2dbcProjection.atLeastOnce**: creates a projection that processes events
   with at-least-once delivery semantics. If the projection crashes
   mid-batch, it will re-process some events after recovery. Our handlers are
   written to be idempotent (using `ON CONFLICT ... DO UPDATE`), so
   reprocessing is safe.

3. **ShardedDaemonProcess**: runs projection instances as managed actors
   distributed across the cluster. If a node goes down, another node picks up
   its slice ranges automatically.

### ProjectionBootstrap

All projections are initialized in `ProjectionBootstrap.start`, which runs
once at application startup from the `Guardian` root actor.

```scala
object ProjectionBootstrap:

  def start(system: ActorSystem[?]): Unit =
    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext

    initProjection[TaskEvent](
      "task-projection",
      "Task",
      () => TaskProjectionHandler()
    )

    initProjection[ConsolidationGroupEvent](
      "consolidation-group-projection",
      "ConsolidationGroup",
      () => ConsolidationGroupProjectionHandler()
    )

    initProjection[TransportOrderEvent](
      "transport-order-projection",
      "TransportOrder",
      () => TransportOrderProjectionHandler()
    )

    // ... one initProjection call per event-sourced aggregate
```

<small>*File: app/src/main/scala/neon/app/projection/ProjectionBootstrap.scala*</small>

The pattern is consistent: every event-sourced aggregate gets its own
projection, identified by a name string and the entity type tag that matches
what the actor uses in `.withTagger`. The handler factory is a zero-argument
function that creates a fresh handler instance.

### The initProjection Helper

The private `initProjection` method wires together the three building blocks:

```scala
private def initProjection[E](
    name: String,
    entityType: String,
    handlerFactory: () => R2dbcHandler[EventEnvelope[E]]
)(using system: ActorSystem[?]): Unit =
  val sliceRanges = EventSourcedProvider.sliceRanges(
    system,
    R2dbcReadJournal.Identifier,
    numberOfRanges = 1
  )
  ShardedDaemonProcess(system).init(
    name = name,
    numberOfInstances = 1,
    behaviorFactory = { index =>
      val sliceRange = sliceRanges(index)
      val sourceProvider =
        EventSourcedProvider.eventsBySlices[E](
          system,
          readJournalPluginId = R2dbcReadJournal.Identifier,
          entityType = entityType,
          minSlice = sliceRange.min,
          maxSlice = sliceRange.max
        )
      ProjectionBehavior(
        R2dbcProjection.atLeastOnce(
          projectionId = ProjectionId(name, sliceRange.min.toString),
          settings = None,
          sourceProvider = sourceProvider,
          handler = handlerFactory
        )
      )
    },
    stopMessage = ProjectionBehavior.Stop
  )
```

<small>*File: app/src/main/scala/neon/app/projection/ProjectionBootstrap.scala*</small>

Let's walk through this step by step:

1. **Slice ranges**: We ask for `numberOfRanges = 1`, meaning one projection
   instance handles all 1024 slices. For higher throughput, we could split
   into multiple ranges (say 4), and `ShardedDaemonProcess` would run four
   instances across the cluster, each responsible for 256 slices.

2. **Source provider**: `EventSourcedProvider.eventsBySlices` creates a
   streaming source that reads events tagged with `entityType` from the R2DBC
   journal. It delivers events in order within each persistence ID.

3. **Projection behavior**: `R2dbcProjection.atLeastOnce` wraps the source
   and handler into a `ProjectionBehavior` actor. This actor manages offset
   tracking (so it knows where to resume after restart) and calls the handler
   for each event envelope.

4. **ShardedDaemonProcess**: starts the projection as a cluster singleton
   (since we have one instance). If the owning node crashes, the daemon
   process migrates to another node and resumes from the last committed
   offset.

> **Note:** The `settings = None` parameter means we accept Pekko's default
> projection settings. In production you might tune `saveOffsetAfterEnvelopes`
> or `saveOffsetAfterDuration` to control how frequently offsets are committed.


## Walkthrough: TaskProjectionHandler

Now let's see what happens inside a handler when an event arrives. The
`TaskProjectionHandler` is a representative example that populates two
read-side tables: `task_by_wave` and `task_by_handling_unit`.

```scala
class TaskProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[TaskEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[TaskEvent]
  ): Future[Done] =
    envelope.event match
      case e: TaskEvent.TaskCreated =>
        val stmt = session.createStatement(
          """INSERT INTO task_by_wave
            |  (task_id, wave_id, order_id, handling_unit_id, state)
            |VALUES ($1, $2, $3, $4, $5)
            |ON CONFLICT (task_id) DO UPDATE SET state = $5""".stripMargin
        )
        stmt.bind(0, e.taskId.value)
        bindOptionalUuid(stmt, 1, e.waveId.map(_.value))
        stmt.bind(2, e.orderId.value)
        bindOptionalUuid(stmt, 3, e.handlingUnitId.map(_.value))
        stmt.bind(4, "Planned")

        // Also insert into task_by_handling_unit if present
        val insertHandlingUnit = e.handlingUnitId.map { handlingUnitId =>
          val stmt2 = session.createStatement(
            """INSERT INTO task_by_handling_unit
              |  (task_id, handling_unit_id, wave_id, order_id, state)
              |VALUES ($1, $2, $3, $4, $5)
              |ON CONFLICT (task_id) DO UPDATE SET state = $5""".stripMargin
          )
          stmt2.bind(0, e.taskId.value)
          stmt2.bind(1, handlingUnitId.value)
          bindOptionalUuid(stmt2, 2, e.waveId.map(_.value))
          stmt2.bind(3, e.orderId.value)
          stmt2.bind(4, "Planned")
          stmt2
        }

        session.updateOne(stmt).flatMap { _ =>
          insertHandlingUnit match
            case Some(s) => session.updateOne(s).map(_ => Done)
            case None    => Future.successful(Done)
        }

      case e: TaskEvent.TaskAllocated =>
        updateState(session, e.taskId.value, "Allocated")
      case e: TaskEvent.TaskAssigned =>
        updateState(session, e.taskId.value, "Assigned")
      case e: TaskEvent.TaskCompleted =>
        updateState(session, e.taskId.value, "Completed")
      case e: TaskEvent.TaskCancelled =>
        updateState(session, e.taskId.value, "Cancelled")
```

<small>*File: app/src/main/scala/neon/app/projection/TaskProjectionHandler.scala*</small>

Several patterns stand out:

**Pattern matching on the event sealed trait.** Every event type gets its own
case. For `TaskCreated`, we insert a new row. For state-change events
(`TaskAllocated`, `TaskAssigned`, `TaskCompleted`, `TaskCancelled`), we
update the `state` column.

**Idempotent writes with ON CONFLICT.** The `INSERT ... ON CONFLICT (task_id)
DO UPDATE` clause means replaying the same event twice produces the same
result. This is essential for at-least-once delivery.

**Two tables from one event.** `TaskCreated` writes to both `task_by_wave`
and `task_by_handling_unit`. This denormalization lets us query tasks
efficiently by either dimension without joins.

**Optional UUID binding.** Tasks may or may not belong to a wave (standalone
tasks have `waveId = None`). The `bindOptionalUuid` helper binds `NULL` when
the value is absent:

```scala
private def bindOptionalUuid(
    stmt: io.r2dbc.spi.Statement,
    index: Int,
    value: Option[UUID]
): Unit =
  value match
    case Some(v) => stmt.bind(index, v)
    case None    => stmt.bindNull(index, classOf[UUID])
```

<small>*File: app/src/main/scala/neon/app/projection/TaskProjectionHandler.scala*</small>

**Shared updateState helper.** Four of the five event types need the same
operation (update two tables), so we factor that into a private method:

```scala
private def updateState(
    session: R2dbcSession,
    taskId: UUID,
    state: String
): Future[Done] =
  val stmt1 = session
    .createStatement(
      "UPDATE task_by_wave SET state = $1 WHERE task_id = $2"
    )
    .bind(0, state)
    .bind(1, taskId)
  val stmt2 = session
    .createStatement(
      "UPDATE task_by_handling_unit SET state = $1 WHERE task_id = $2"
    )
    .bind(0, state)
    .bind(1, taskId)
  session
    .updateOne(stmt1)
    .flatMap(_ => session.updateOne(stmt2))
    .map(_ => Done)
```

<small>*File: app/src/main/scala/neon/app/projection/TaskProjectionHandler.scala*</small>


## The Full Projection Catalogue

Neon WES ships with thirteen projections. Each one follows the same pattern
we just studied. Here is the complete catalogue:

| Projection | Handler Class | Read-Side Table | Key Columns |
|---|---|---|---|
| `task-projection` | `TaskProjectionHandler` | `task_by_wave`, `task_by_handling_unit` | `task_id`, `wave_id`, `state` |
| `consolidation-group-projection` | `ConsolidationGroupProjectionHandler` | `consolidation_group_by_wave` | `consolidation_group_id`, `wave_id`, `state` |
| `transport-order-projection` | `TransportOrderProjectionHandler` | `transport_order_by_handling_unit` | `transport_order_id`, `handling_unit_id`, `state` |
| `workstation-projection` | `WorkstationProjectionHandler` | `workstation_by_type_and_state` | `workstation_id`, `workstation_type`, `state` |
| `handling-unit-projection` | `HandlingUnitProjectionHandler` | `handling_unit_lookup` | `handling_unit_id`, `packaging_level`, `state` |
| `slot-projection` | `SlotProjectionHandler` | `slot_by_workstation` | `slot_id`, `workstation_id`, `order_id`, `state` |
| `inventory-projection` | `InventoryProjectionHandler` | `inventory_by_location_sku_lot` | `inventory_id`, `location_id`, `sku_id`, `on_hand`, `reserved` |
| `stock-position-projection` | `StockPositionProjectionHandler` | `stock_position_by_sku_area` | `stock_position_id`, `sku_id`, `warehouse_area_id` |
| `handling-unit-stock-projection` | `HandlingUnitStockProjectionHandler` | `handling_unit_stock_by_container` | `handling_unit_stock_id`, `sku_id`, `container_id` |
| `inbound-delivery-projection` | `InboundDeliveryProjectionHandler` | `inbound_delivery_by_state` | `inbound_delivery_id`, `sku_id`, `state` |
| `goods-receipt-projection` | `GoodsReceiptProjectionHandler` | `goods_receipt_by_delivery` | `goods_receipt_id`, `inbound_delivery_id`, `state` |
| `cycle-count-projection` | `CycleCountProjectionHandler` | `cycle_count_by_state` | `cycle_count_id`, `warehouse_area_id`, `state` |
| `count-task-projection` | `CountTaskProjectionHandler` | `count_task_by_cycle_count` | `count_task_id`, `cycle_count_id`, `state` |

Notice the naming convention in the read-side tables. Each table name
describes the primary query dimension: `task_by_wave` is optimized for
"find tasks by wave ID," `workstation_by_type_and_state` for "find
workstations by type and current state," and so on. This is a deliberate
CQRS practice: name your read model tables after the queries they serve.

Some projections are more complex than others. `StockPositionProjectionHandler`
handles ten different event types (Created, Allocated, Deallocated,
QuantityAdded, AllocatedConsumed, Reserved, ReservationReleased, Blocked,
Unblocked, Adjusted, StatusChanged) because stock positions have rich
quantity semantics. `GoodsReceiptProjectionHandler` is simpler, tracking just
Open, Confirmed, and Cancelled states.

> **Note:** Every handler class lives in the `app` module under
> `neon.app.projection`. The `app` module is the only one that depends on all
> domain modules, so it has visibility into every event type. Domain modules
> themselves know nothing about projections.


## LoggingProjectionHandler Base Class

Every projection handler in Neon WES extends `LoggingProjectionHandler`
rather than `R2dbcHandler` directly. This base class adds structured logging
around event processing:

```scala
abstract class LoggingProjectionHandler[E](using
    ExecutionContext
) extends R2dbcHandler[EventEnvelope[E]]
    with LazyLogging:

  final override def process(
      session: R2dbcSession,
      envelope: EventEnvelope[E]
  ): Future[Done] =
    logger.debug(
      "Processing {} for {}",
      envelope.event.getClass.getSimpleName,
      envelope.persistenceId
    )
    processEvent(session, envelope).recoverWith { case exception =>
      logger.error(
        "Projection failed for {}",
        envelope.persistenceId,
        exception
      )
      Future.failed(exception)
    }

  protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[E]
  ): Future[Done]
```

<small>*File: app/src/main/scala/neon/app/projection/LoggingProjectionHandler.scala*</small>

The design is a straightforward application of the Template Method pattern:

1. `process` is `final`, so subclasses cannot bypass logging.
2. On entry, it logs a DEBUG message with the event class name and persistence
   ID (e.g., `"Processing TaskCompleted for Task|abc-123"`).
3. It delegates to the abstract `processEvent` method that subclasses
   implement.
4. If `processEvent` fails, it logs an ERROR with the full stack trace, then
   re-throws so Pekko's projection supervision can handle the failure
   (typically by retrying or restarting the projection).

This means we get consistent observability across all thirteen projections
without any boilerplate in the concrete handlers. When a projection falls
behind or starts failing, the ERROR logs immediately tell us which entity
and which event type caused the problem.

> **Note:** The `LazyLogging` trait from scala-logging creates one logger per
> concrete subclass, so log messages automatically include the handler class
> name in the logger context. You can set per-handler log levels in your
> Logback configuration if needed.


## What Comes Next

We now have a complete pipeline: commands flow into actors (write side),
events are persisted to the journal, and projections consume those events to
populate query-optimized tables (read side). But those read-side tables are
not much use if we cannot expose them through an API.

In the next chapter, we will build the HTTP layer that ties everything
together. We will see how Pekko HTTP routes call async services, how circe
provides JSON marshalling with zero boilerplate, how domain error ADTs map
cleanly to HTTP status codes, and how session-based authentication protects
every endpoint.
