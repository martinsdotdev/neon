package neon.app.projection

import neon.consolidationgroup.ConsolidationGroupEvent
import neon.handlingunit.HandlingUnitActor
import neon.inventory.InventoryEvent
import neon.slot.SlotActor
import neon.task.TaskEvent
import neon.transportorder.TransportOrderEvent
import neon.workstation.WorkstationActor
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
import org.apache.pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcProjection
import org.apache.pekko.projection.{ProjectionBehavior, ProjectionId}

import scala.concurrent.ExecutionContext

/** Initializes all CQRS read-side projections at application startup.
  *
  * Each projection consumes events by slice from the R2DBC journal via
  * `EventSourcedProvider.eventsBySlices` and processes them through a handler that upserts into
  * read-side PostgreSQL tables.
  */
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

    initProjection[WorkstationActor.ActorEvent](
      "workstation-projection",
      "Workstation",
      () => WorkstationProjectionHandler()
    )

    initProjection[HandlingUnitActor.ActorEvent](
      "handling-unit-projection",
      "HandlingUnit",
      () => HandlingUnitProjectionHandler()
    )

    initProjection[SlotActor.ActorEvent](
      "slot-projection",
      "Slot",
      () => SlotProjectionHandler()
    )

    initProjection[InventoryEvent](
      "inventory-projection",
      "Inventory",
      () => InventoryProjectionHandler()
    )

  private def initProjection[E](
      name: String,
      entityType: String,
      handlerFactory: () => org.apache.pekko.projection.r2dbc.scaladsl.R2dbcHandler[EventEnvelope[
        E
      ]]
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
            projectionId = ProjectionId(
              name,
              sliceRange.min.toString
            ),
            settings = None,
            sourceProvider = sourceProvider,
            handler = handlerFactory
          )
        )
      },
      stopMessage = ProjectionBehavior.Stop
    )
