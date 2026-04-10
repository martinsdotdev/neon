package neon.handlingunit

import neon.common.serialization.CborSerializable
import neon.common.{
  AdjustmentReasonCode,
  ContainerId,
  HandlingUnitStockId,
  InventoryStatus,
  SkuId,
  SlotCode,
  StockLockType,
  StockPositionId
}

import java.time.Instant

/** Domain events for the [[HandlingUnitStock]] aggregate. */
sealed trait HandlingUnitStockEvent extends CborSerializable

object HandlingUnitStockEvent:

  case class Created(
      handlingUnitStockId: HandlingUnitStockId,
      skuId: SkuId,
      containerId: ContainerId,
      slotCode: SlotCode,
      stockPositionId: StockPositionId,
      physicalContainer: Boolean,
      onHandQuantity: Int,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class Allocated(
      handlingUnitStockId: HandlingUnitStockId,
      quantity: Int,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class Deallocated(
      handlingUnitStockId: HandlingUnitStockId,
      quantity: Int,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class QuantityAdded(
      handlingUnitStockId: HandlingUnitStockId,
      quantity: Int,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class AllocatedConsumed(
      handlingUnitStockId: HandlingUnitStockId,
      quantity: Int,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class Reserved(
      handlingUnitStockId: HandlingUnitStockId,
      quantity: Int,
      lockType: StockLockType,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class ReservationReleased(
      handlingUnitStockId: HandlingUnitStockId,
      quantity: Int,
      lockType: StockLockType,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class Blocked(
      handlingUnitStockId: HandlingUnitStockId,
      quantity: Int,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class Unblocked(
      handlingUnitStockId: HandlingUnitStockId,
      quantity: Int,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class Adjusted(
      handlingUnitStockId: HandlingUnitStockId,
      delta: Int,
      reasonCode: AdjustmentReasonCode,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent

  case class StatusChanged(
      handlingUnitStockId: HandlingUnitStockId,
      previousStatus: InventoryStatus,
      newStatus: InventoryStatus,
      occurredAt: Instant
  ) extends HandlingUnitStockEvent
