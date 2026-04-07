package neon.app.projection

import neon.consolidationgroup.{ConsolidationGroupActor, ConsolidationGroupEvent}
import neon.handlingunit.HandlingUnitActor
import neon.inventory.{InventoryActor, InventoryEvent}
import neon.slot.SlotActor
import neon.task.{TaskActor, TaskEvent}
import neon.transportorder.{TransportOrderActor, TransportOrderEvent}
import neon.workstation.WorkstationActor

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
import org.apache.pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcProjection
import org.apache.pekko.projection.scaladsl.SourceProvider
import org.apache.pekko.projection.{ProjectionBehavior, ProjectionId}

import scala.concurrent.ExecutionContext

/** Initializes all CQRS read-side projections at application startup.
  *
  * Each projection consumes tagged events from the journal via `EventSourcedProvider.eventsByTag`
  * and processes them through a handler that upserts into read-side PostgreSQL tables.
  */
object ProjectionBootstrap:

  def start(system: ActorSystem[?]): Unit =
    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext

    initProjection[TaskEvent](
      "task-projection",
      "task",
      () => TaskProjectionHandler()
    )

    initProjection[ConsolidationGroupEvent](
      "consolidation-group-projection",
      "consolidation-group",
      () => ConsolidationGroupProjectionHandler()
    )

    initProjection[TransportOrderEvent](
      "transport-order-projection",
      "transport-order",
      () => TransportOrderProjectionHandler()
    )

    initProjection[WorkstationActor.ActorEvent](
      "workstation-projection",
      "workstation",
      () => WorkstationProjectionHandler()
    )

    initProjection[HandlingUnitActor.ActorEvent](
      "handling-unit-projection",
      "handling-unit",
      () => HandlingUnitProjectionHandler()
    )

    initProjection[SlotActor.ActorEvent](
      "slot-projection",
      "slot",
      () => SlotProjectionHandler()
    )

    initProjection[InventoryEvent](
      "inventory-projection",
      "inventory",
      () => InventoryProjectionHandler()
    )

  private def initProjection[E](
      name: String,
      tag: String,
      handlerFactory: () => org.apache.pekko.projection.r2dbc.scaladsl.R2dbcHandler[EventEnvelope[
        E
      ]]
  )(using system: ActorSystem[?]): Unit =
    ShardedDaemonProcess(system).init(
      name = name,
      numberOfInstances = 1,
      behaviorFactory = { _ =>
        val sourceProvider: SourceProvider[
          org.apache.pekko.persistence.query.Offset,
          EventEnvelope[E]
        ] =
          EventSourcedProvider.eventsByTag[E](
            system,
            readJournalPluginId = R2dbcReadJournal.Identifier,
            tag = tag
          )
        ProjectionBehavior(
          R2dbcProjection.exactlyOnce(
            projectionId = ProjectionId(name, tag),
            settings = None.orNull,
            sourceProvider = sourceProvider,
            handler = handlerFactory
          )
        )
      },
      stopMessage = ProjectionBehavior.Stop
    )
