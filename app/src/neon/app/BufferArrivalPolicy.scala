package neon.app

import neon.handlingunit.{HandlingUnit, HandlingUnitEvent}
import neon.transportorder.TransportOrderEvent

import java.time.Instant

/** Moves a [[HandlingUnit.PickCreated]] to [[HandlingUnit.InBuffer]] when the corresponding
  * transport order is confirmed at the buffer destination.
  */
object BufferArrivalPolicy:

  /** Transitions the handling unit to InBuffer using the confirmed transport order's destination.
    *
    * @param event
    *   the transport order confirmation event
    * @param handlingUnit
    *   the pick handling unit to move to buffer
    * @param at
    *   instant of the buffer arrival
    * @return
    *   in-buffer handling unit and movement event
    */
  def apply(
      event: TransportOrderEvent.TransportOrderConfirmed,
      handlingUnit: HandlingUnit.PickCreated,
      at: Instant
  ): (HandlingUnit.InBuffer, HandlingUnitEvent.HandlingUnitMovedToBuffer) =
    handlingUnit.moveToBuffer(event.destination, at)
