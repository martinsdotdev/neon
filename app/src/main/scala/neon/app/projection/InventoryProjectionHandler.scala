package neon.app.projection

import neon.inventory.InventoryEvent
import neon.inventory.InventoryProjectionSchema.InventoryByLocationSkuLot
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import scala.concurrent.{ExecutionContext, Future}

/** Populates `inventory_by_location_sku_lot` from inventory events. */
class InventoryProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[InventoryEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[InventoryEvent]
  ): Future[Done] =
    envelope.event match
      case e: InventoryEvent.InventoryCreated =>
        val stmt = session
          .createStatement(InventoryByLocationSkuLot.Upsert)
          .bind(0, e.inventoryId.value)
          .bind(1, e.locationId.value)
          .bind(2, e.skuId.value)
        e.lot match
          case Some(l) => stmt.bind(3, l.value)
          case None    => stmt.bindNull(3, classOf[String])
        stmt.bind(4, e.onHand)
        session.updateOne(stmt).map(_ => Done)

      case e: InventoryEvent.InventoryReserved =>
        val stmt = session
          .createStatement(InventoryByLocationSkuLot.IncrementReserved)
          .bind(0, e.quantityReserved)
          .bind(1, e.inventoryId.value)
        session.updateOne(stmt).map(_ => Done)

      case e: InventoryEvent.InventoryReleased =>
        val stmt = session
          .createStatement(InventoryByLocationSkuLot.DecrementReserved)
          .bind(0, e.quantityReleased)
          .bind(1, e.inventoryId.value)
        session.updateOne(stmt).map(_ => Done)

      case e: InventoryEvent.InventoryConsumed =>
        val stmt = session
          .createStatement(InventoryByLocationSkuLot.DecrementOnHandAndReserved)
          .bind(0, e.quantityConsumed)
          .bind(1, e.inventoryId.value)
        session.updateOne(stmt).map(_ => Done)

      case e: InventoryEvent.LotCorrected =>
        val stmt = session
          .createStatement(InventoryByLocationSkuLot.UpdateLot)
        e.newLot match
          case Some(l) => stmt.bind(0, l.value)
          case None    => stmt.bindNull(0, classOf[String])
        stmt.bind(1, e.inventoryId.value)
        session.updateOne(stmt).map(_ => Done)
