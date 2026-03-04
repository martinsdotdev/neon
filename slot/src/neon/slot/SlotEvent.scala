package neon.slot

import neon.common.{HandlingUnitId, OrderId, SlotId, WorkstationId}

import java.time.Instant

/** Domain events emitted by [[Slot]] state transitions. */
sealed trait SlotEvent:
  /** The slot that emitted this event. */
  def slotId: SlotId

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for [[Slot]]. */
object SlotEvent:
  /** Emitted when a slot is reserved for an order.
    *
    * @param slotId
    *   the slot identifier
    * @param workstationId
    *   the workstation owning this slot
    * @param orderId
    *   the order assigned to this slot
    * @param handlingUnitId
    *   the ship handling unit for this slot
    * @param occurredAt
    *   instant of the reservation
    */
  case class SlotReserved(
      slotId: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId,
      occurredAt: Instant
  ) extends SlotEvent

  /** Emitted when a reserved slot completes processing.
    *
    * @param slotId
    *   the slot identifier
    * @param workstationId
    *   the workstation owning this slot
    * @param orderId
    *   the order that was processed
    * @param handlingUnitId
    *   the ship handling unit that was filled
    * @param occurredAt
    *   instant of completion
    */
  case class SlotCompleted(
      slotId: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId,
      occurredAt: Instant
  ) extends SlotEvent

  /** Emitted when a reserved slot is released back to available. The `handlingUnitId` is omitted
    * because the binding is dissolved on release.
    *
    * @param slotId
    *   the slot identifier
    * @param workstationId
    *   the workstation owning this slot
    * @param orderId
    *   the order whose reservation was released
    * @param occurredAt
    *   instant of the release
    */
  case class SlotReleased(
      slotId: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      occurredAt: Instant
  ) extends SlotEvent
