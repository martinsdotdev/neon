package neon.app.projection

import neon.stockposition.StockPositionEvent
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
          .createStatement(
            """INSERT INTO stock_position_by_sku_area
              |  (stock_position_id, sku_id, warehouse_area_id, status,
              |   expiration_date, on_hand_quantity, available_quantity,
              |   allocated_quantity, reserved_quantity, blocked_quantity)
              |VALUES ($1, $2, $3, $4, $5, $6, $6, 0, 0, 0)
              |ON CONFLICT (stock_position_id) DO UPDATE SET
              |  on_hand_quantity = $6, available_quantity = $6""".stripMargin
          )
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
          "available_quantity = available_quantity - $1, allocated_quantity = allocated_quantity + $1",
          e.quantity
        )

      case e: StockPositionEvent.Deallocated =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          "available_quantity = available_quantity + $1, allocated_quantity = allocated_quantity - $1",
          e.quantity
        )

      case e: StockPositionEvent.QuantityAdded =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          "on_hand_quantity = on_hand_quantity + $1, available_quantity = available_quantity + $1",
          e.quantity
        )

      case e: StockPositionEvent.AllocatedConsumed =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          "on_hand_quantity = on_hand_quantity - $1, allocated_quantity = allocated_quantity - $1",
          e.quantity
        )

      case e: StockPositionEvent.Reserved =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          "available_quantity = available_quantity - $1, reserved_quantity = reserved_quantity + $1",
          e.quantity
        )

      case e: StockPositionEvent.ReservationReleased =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          "available_quantity = available_quantity + $1, reserved_quantity = reserved_quantity - $1",
          e.quantity
        )

      case e: StockPositionEvent.Blocked =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          "available_quantity = available_quantity - $1, blocked_quantity = blocked_quantity + $1",
          e.quantity
        )

      case e: StockPositionEvent.Unblocked =>
        updateQuantities(
          session,
          e.stockPositionId.value,
          "available_quantity = available_quantity + $1, blocked_quantity = blocked_quantity - $1",
          e.quantity
        )

      case e: StockPositionEvent.Adjusted =>
        val stmt = session
          .createStatement(
            """UPDATE stock_position_by_sku_area
              |SET on_hand_quantity = on_hand_quantity + $1,
              |    available_quantity = available_quantity + $1
              |WHERE stock_position_id = $2""".stripMargin
          )
          .bind(0, e.delta)
          .bind(1, e.stockPositionId.value)
        session.updateOne(stmt).map(_ => Done)

      case e: StockPositionEvent.StatusChanged =>
        val stmt = session
          .createStatement(
            "UPDATE stock_position_by_sku_area SET status = $1 WHERE stock_position_id = $2"
          )
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
      .createStatement(
        s"UPDATE stock_position_by_sku_area SET $setClause WHERE stock_position_id = $$2"
      )
      .bind(0, quantity)
      .bind(1, stockPositionId)
    session.updateOne(stmt).map(_ => Done)
