package neon.slot

import neon.common.{HandlingUnitId, OrderId, SlotId, WorkstationId}

import java.time.Instant

sealed trait SlotEvent:
  def slotId: SlotId
  def occurredAt: Instant

object SlotEvent:
  case class SlotReserved(
      slotId: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId,
      occurredAt: Instant
  ) extends SlotEvent

  case class SlotCompleted(
      slotId: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId,
      occurredAt: Instant
  ) extends SlotEvent

  case class SlotReleased(
      slotId: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      occurredAt: Instant
  ) extends SlotEvent
