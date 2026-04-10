package neon.app.projection

import neon.handlingunit.HandlingUnitStockEvent
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
          .createStatement(
            """INSERT INTO handling_unit_stock_by_container
              |  (handling_unit_stock_id, sku_id, stock_position_id, container_id,
              |   slot_code, on_hand_quantity, available_quantity,
              |   allocated_quantity, reserved_quantity, blocked_quantity)
              |VALUES ($1, $2, $3, $4, $5, $6, $6, 0, 0, 0)
              |ON CONFLICT (handling_unit_stock_id) DO UPDATE SET
              |  on_hand_quantity = $6, available_quantity = $6""".stripMargin
          )
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
          "available_quantity = available_quantity - $1, allocated_quantity = allocated_quantity + $1",
          e.quantity
        )

      case e: HandlingUnitStockEvent.Deallocated =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          "available_quantity = available_quantity + $1, allocated_quantity = allocated_quantity - $1",
          e.quantity
        )

      case e: HandlingUnitStockEvent.QuantityAdded =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          "on_hand_quantity = on_hand_quantity + $1, available_quantity = available_quantity + $1",
          e.quantity
        )

      case e: HandlingUnitStockEvent.AllocatedConsumed =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          "on_hand_quantity = on_hand_quantity - $1, allocated_quantity = allocated_quantity - $1",
          e.quantity
        )

      case e: HandlingUnitStockEvent.Reserved =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          "available_quantity = available_quantity - $1, reserved_quantity = reserved_quantity + $1",
          e.quantity
        )

      case e: HandlingUnitStockEvent.ReservationReleased =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          "available_quantity = available_quantity + $1, reserved_quantity = reserved_quantity - $1",
          e.quantity
        )

      case e: HandlingUnitStockEvent.Blocked =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          "available_quantity = available_quantity - $1, blocked_quantity = blocked_quantity + $1",
          e.quantity
        )

      case e: HandlingUnitStockEvent.Unblocked =>
        updateQuantities(
          session,
          e.handlingUnitStockId.value,
          "available_quantity = available_quantity + $1, blocked_quantity = blocked_quantity - $1",
          e.quantity
        )

      case e: HandlingUnitStockEvent.Adjusted =>
        val stmt = session
          .createStatement(
            """UPDATE handling_unit_stock_by_container
              |SET on_hand_quantity = on_hand_quantity + $1,
              |    available_quantity = available_quantity + $1
              |WHERE handling_unit_stock_id = $2""".stripMargin
          )
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
      .createStatement(
        s"UPDATE handling_unit_stock_by_container SET $setClause WHERE handling_unit_stock_id = $$2"
      )
      .bind(0, quantity)
      .bind(1, handlingUnitStockId)
    session.updateOne(stmt).map(_ => Done)
