package neon.slot

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{HandlingUnitId, OrderId, SlotId, WorkstationId}

import java.time.Instant

/** Typestate-encoded slot aggregate representing a put-wall position within a workstation.
  *
  * Lifecycle: [[Available]] -> [[Reserved]] -> [[Completed]], with `release()` on [[Reserved]]
  * returning to [[Available]] for pre-placement cancellation. One slot per order.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait Slot:
  /** The slot identifier. */
  def id: SlotId

  /** The workstation this slot belongs to. */
  def workstationId: WorkstationId

/** State definitions for [[Slot]]. */
object Slot:
  /** A slot that is open and ready to be reserved for an order. */
  case class Available(
      id: SlotId,
      workstationId: WorkstationId
  ) extends Slot:
    /** Reserves this slot for an order, transitioning from [[Available]] to [[Reserved]].
      *
      * @param orderId
      *   the order assigned to this slot
      * @param handlingUnitId
      *   the ship handling unit for this slot
      * @param at
      *   instant of the reservation
      * @return
      *   reserved state and reservation event
      */
    def reserve(
        orderId: OrderId,
        handlingUnitId: HandlingUnitId,
        at: Instant
    ): (Reserved, SlotEvent.SlotReserved) =
      val reserved = Reserved(id, workstationId, orderId, handlingUnitId)
      val event =
        SlotEvent.SlotReserved(id, workstationId, orderId, handlingUnitId, at)
      (reserved, event)

  /** A slot reserved for a specific order, holding a ship handling unit. */
  case class Reserved(
      id: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId
  ) extends Slot:
    /** Completes this slot, transitioning from [[Reserved]] to [[Completed]].
      *
      * @param at
      *   instant of completion
      * @return
      *   completed state and completion event
      */
    def complete(at: Instant): (Completed, SlotEvent.SlotCompleted) =
      val completed = Completed(id, workstationId, orderId, handlingUnitId)
      val event =
        SlotEvent.SlotCompleted(id, workstationId, orderId, handlingUnitId, at)
      (completed, event)

    /** Releases this slot back to [[Available]], dissolving the order binding.
      *
      * Used for pre-placement cancellation before items have been placed in the slot.
      *
      * @param at
      *   instant of the release
      * @return
      *   available state and released event
      */
    def release(at: Instant): (Available, SlotEvent.SlotReleased) =
      val available = Available(id, workstationId)
      val event = SlotEvent.SlotReleased(id, workstationId, orderId, at)
      (available, event)

  /** A slot whose order has been fully processed. Terminal state. */
  case class Completed(
      id: SlotId,
      workstationId: WorkstationId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId
  ) extends Slot
