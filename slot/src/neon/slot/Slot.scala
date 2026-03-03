package neon.slot

import neon.common.{HandlingUnitId, OrderId, SlotId, WorkstationId}

import java.time.Instant

sealed trait Slot:
  def id: SlotId
  def workstationId: WorkstationId

object Slot:
  case class Available(
      id: SlotId,
      workstationId: WorkstationId
  ) extends Slot:
    def reserve(
        orderId: OrderId,
        handlingUnitId: HandlingUnitId,
        at: Instant
    ): (Reserved, SlotEvent.SlotReserved) =
      val reserved = Reserved(id, workstationId, orderId, handlingUnitId)
      val event =
        SlotEvent.SlotReserved(id, workstationId, orderId, handlingUnitId, at)
      (reserved, event)

  case class Reserved(
      id: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId
  ) extends Slot:
    def complete(at: Instant): (Completed, SlotEvent.SlotCompleted) =
      val completed = Completed(id, workstationId, orderId, handlingUnitId)
      val event =
        SlotEvent.SlotCompleted(id, workstationId, orderId, handlingUnitId, at)
      (completed, event)

    def release(at: Instant): (Available, SlotEvent.SlotReleased) =
      val available = Available(id, workstationId)
      val event = SlotEvent.SlotReleased(id, workstationId, orderId, at)
      (available, event)

  case class Completed(
      id: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId
  ) extends Slot
