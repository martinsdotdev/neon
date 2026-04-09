package neon.stockposition

import neon.common.serialization.CborSerializable
import neon.common.{
  AdjustmentReasonCode,
  InventoryStatus,
  LotAttributes,
  SkuId,
  StockLockType,
  StockPositionId,
  WarehouseAreaId
}

import java.time.Instant

/** Domain events for the [[StockPosition]] aggregate. */
sealed trait StockPositionEvent extends CborSerializable

object StockPositionEvent:

  case class Created(
      stockPositionId: StockPositionId,
      skuId: SkuId,
      warehouseAreaId: WarehouseAreaId,
      lotAttributes: LotAttributes,
      onHandQuantity: Int,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class Allocated(
      stockPositionId: StockPositionId,
      quantity: Int,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class Deallocated(
      stockPositionId: StockPositionId,
      quantity: Int,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class QuantityAdded(
      stockPositionId: StockPositionId,
      quantity: Int,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class AllocatedConsumed(
      stockPositionId: StockPositionId,
      quantity: Int,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class Reserved(
      stockPositionId: StockPositionId,
      quantity: Int,
      lockType: StockLockType,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class ReservationReleased(
      stockPositionId: StockPositionId,
      quantity: Int,
      lockType: StockLockType,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class Blocked(
      stockPositionId: StockPositionId,
      quantity: Int,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class Unblocked(
      stockPositionId: StockPositionId,
      quantity: Int,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class Adjusted(
      stockPositionId: StockPositionId,
      delta: Int,
      reasonCode: AdjustmentReasonCode,
      occurredAt: Instant
  ) extends StockPositionEvent

  case class StatusChanged(
      stockPositionId: StockPositionId,
      previousStatus: InventoryStatus,
      newStatus: InventoryStatus,
      occurredAt: Instant
  ) extends StockPositionEvent
