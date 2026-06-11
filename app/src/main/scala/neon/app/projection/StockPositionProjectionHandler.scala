package neon.app.projection

import neon.stockposition.StockPositionEvent
import neon.stockposition.StockPositionProjectionSchema.StockPositionBySkuArea
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Populates `stock_position_by_sku_area` from stock position events. */
class StockPositionProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[StockPositionEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[StockPositionEvent]
  ): Future[Done] =
    envelope.event match
      case e: StockPositionEvent.Created =>
        val stmt = session
          .createStatement(StockPositionBySkuArea.Upsert)
          .bind(0, e.stockPositionId.value)
          .bind(1, e.skuId.value)
          .bind(2, e.warehouseAreaId.value)
          .bind(3, "Available")
        e.lotAttributes.expirationDate match
          case Some(d) => stmt.bind(4, d)
          case None    => stmt.bindNull(4, classOf[LocalDate])
        stmt.bind(5, e.onHandQuantity)
        session.updateOne(stmt).map(_ => Done)

      case e: StockPositionEvent.Allocated =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          StockPositionBySkuArea.AllocateSetClause,
          e.quantity
        )

      case e: StockPositionEvent.Deallocated =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          StockPositionBySkuArea.DeallocateSetClause,
          e.quantity
        )

      case e: StockPositionEvent.QuantityAdded =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          StockPositionBySkuArea.AddQuantitySetClause,
          e.quantity
        )

      case e: StockPositionEvent.AllocatedConsumed =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          StockPositionBySkuArea.ConsumeAllocatedSetClause,
          e.quantity
        )

      case e: StockPositionEvent.Reserved =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          StockPositionBySkuArea.ReserveSetClause,
          e.quantity
        )

      case e: StockPositionEvent.ReservationReleased =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          StockPositionBySkuArea.ReleaseReservationSetClause,
          e.quantity
        )

      case e: StockPositionEvent.Blocked =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          StockPositionBySkuArea.BlockSetClause,
          e.quantity
        )

      case e: StockPositionEvent.Unblocked =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          StockPositionBySkuArea.UnblockSetClause,
          e.quantity
        )

      case e: StockPositionEvent.Adjusted =>
        val stmt = session
          .createStatement(StockPositionBySkuArea.AdjustQuantities)
          .bind(0, e.delta)
          .bind(1, e.stockPositionId.value)
        session.updateOne(stmt).map(_ => Done)

      case e: StockPositionEvent.StatusChanged =>
        val stmt = session
          .createStatement(StockPositionBySkuArea.UpdateStatus)
          .bind(0, e.newStatus.toString)
          .bind(1, e.stockPositionId.value)
        session.updateOne(stmt).map(_ => Done)

  private def updateQuantities(
      session: R2dbcSession,
      stockPositionId: UUID,
      setClause: String,
      quantity: Int
  ): Future[Done] =
    val stmt = session
      .createStatement(StockPositionBySkuArea.updateQuantitiesStatement(setClause))
      .bind(0, quantity)
      .bind(1, stockPositionId)
    session.updateOne(stmt).map(_ => Done)
