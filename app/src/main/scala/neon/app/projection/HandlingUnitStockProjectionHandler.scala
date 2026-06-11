package neon.app.projection

import neon.handlingunitstock.HandlingUnitStockEvent
import neon.handlingunitstock.HandlingUnitStockProjectionSchema.HandlingUnitStockByContainer
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Populates `handling_unit_stock_by_container` from handling unit stock events. */
class HandlingUnitStockProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[HandlingUnitStockEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[HandlingUnitStockEvent]
  ): Future[Done] =
    envelope.event match
      case e: HandlingUnitStockEvent.Created =>
        val stmt = session
          .createStatement(HandlingUnitStockByContainer.Upsert)
          .bind(0, e.handlingUnitStockId.value)
          .bind(1, e.skuId.value)
          .bind(2, e.stockPositionId.value)
          .bind(3, e.containerId.value)
        stmt.bind(4, e.slotCode.value)
        stmt.bind(5, e.onHandQuantity)
        session.updateOne(stmt).map(_ => Done)

      case e: HandlingUnitStockEvent.Allocated =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          HandlingUnitStockByContainer.AllocateSetClause,
          e.quantity
        )

      case e: HandlingUnitStockEvent.Deallocated =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          HandlingUnitStockByContainer.DeallocateSetClause,
          e.quantity
        )

      case e: HandlingUnitStockEvent.QuantityAdded =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          HandlingUnitStockByContainer.AddQuantitySetClause,
          e.quantity
        )

      case e: HandlingUnitStockEvent.AllocatedConsumed =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          HandlingUnitStockByContainer.ConsumeAllocatedSetClause,
          e.quantity
        )

      case e: HandlingUnitStockEvent.Reserved =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          HandlingUnitStockByContainer.ReserveSetClause,
          e.quantity
        )

      case e: HandlingUnitStockEvent.ReservationReleased =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          HandlingUnitStockByContainer.ReleaseReservationSetClause,
          e.quantity
        )

      case e: HandlingUnitStockEvent.Blocked =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          HandlingUnitStockByContainer.BlockSetClause,
          e.quantity
        )

      case e: HandlingUnitStockEvent.Unblocked =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          HandlingUnitStockByContainer.UnblockSetClause,
          e.quantity
        )

      case e: HandlingUnitStockEvent.Adjusted =>
        val stmt = session
          .createStatement(HandlingUnitStockByContainer.AdjustQuantities)
          .bind(0, e.delta)
          .bind(1, e.handlingUnitStockId.value)
        session.updateOne(stmt).map(_ => Done)

      case _: HandlingUnitStockEvent.StatusChanged =>
        Future.successful(Done)

  private def updateQuantities(
      session: R2dbcSession,
      handlingUnitStockId: UUID,
      setClause: String,
      quantity: Int
  ): Future[Done] =
    val stmt = session
      .createStatement(HandlingUnitStockByContainer.updateQuantitiesStatement(setClause))
      .bind(0, quantity)
      .bind(1, handlingUnitStockId)
    session.updateOne(stmt).map(_ => Done)
