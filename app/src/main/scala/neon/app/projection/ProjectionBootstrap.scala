package neon.app.projection

import neon.consolidationgroup.ConsolidationGroupEvent
import neon.counttask.CountTaskEvent
import neon.cyclecount.CycleCountEvent
import neon.goodsreceipt.GoodsReceiptEvent
import neon.handlingunit.HandlingUnitActor
import neon.handlingunitstock.HandlingUnitStockEvent
import neon.inbounddelivery.InboundDeliveryEvent
import neon.inventory.InventoryEvent
import neon.slot.SlotActor
import neon.stockposition.StockPositionEvent
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
      name = "task-projection",
      entityType = "Task",
      handlerFactory = () => TaskProjectionHandler()
    )

    initProjection[TaskEvent](
      name = "task-by-assignee-projection",
      entityType = "Task",
      handlerFactory = () => TaskByAssigneeProjectionHandler()
    )

    initProjection[ConsolidationGroupEvent](
      name = "consolidation-group-projection",
      entityType = "ConsolidationGroup",
      handlerFactory = () => ConsolidationGroupProjectionHandler()
    )

    initProjection[TransportOrderEvent](
      name = "transport-order-projection",
      entityType = "TransportOrder",
      handlerFactory = () => TransportOrderProjectionHandler()
    )

    initProjection[WorkstationActor.ActorEvent](
      name = "workstation-projection",
      entityType = "Workstation",
      handlerFactory = () => WorkstationProjectionHandler()
    )

    initProjection[HandlingUnitActor.ActorEvent](
      name = "handling-unit-projection",
      entityType = "HandlingUnit",
      handlerFactory = () => HandlingUnitProjectionHandler()
    )

    initProjection[SlotActor.ActorEvent](
      name = "slot-projection",
      entityType = "Slot",
      handlerFactory = () => SlotProjectionHandler()
    )

    initProjection[InventoryEvent](
      name = "inventory-projection",
      entityType = "Inventory",
      handlerFactory = () => InventoryProjectionHandler()
    )

    initProjection[StockPositionEvent](
      name = "stock-position-projection",
      entityType = "StockPosition",
      handlerFactory = () => StockPositionProjectionHandler()
    )

    initProjection[HandlingUnitStockEvent](
      name = "handling-unit-stock-projection",
      entityType = "HandlingUnitStock",
      handlerFactory = () => HandlingUnitStockProjectionHandler()
    )

    initProjection[InboundDeliveryEvent](
      name = "inbound-delivery-projection",
      entityType = "InboundDelivery",
      handlerFactory = () => InboundDeliveryProjectionHandler()
    )

    initProjection[GoodsReceiptEvent](
      name = "goods-receipt-projection",
      entityType = "GoodsReceipt",
      handlerFactory = () => GoodsReceiptProjectionHandler()
    )

    initProjection[CycleCountEvent](
      name = "cycle-count-projection",
      entityType = "CycleCount",
      handlerFactory = () => CycleCountProjectionHandler()
    )

    initProjection[CountTaskEvent](
      name = "count-task-projection",
      entityType = "CountTask",
      handlerFactory = () => CountTaskProjectionHandler()
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
