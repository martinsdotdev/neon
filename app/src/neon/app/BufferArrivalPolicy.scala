package neon.app

import neon.handlingunit.{HandlingUnit, HandlingUnitEvent}
import neon.transportorder.TransportOrderEvent

import java.time.Instant

object BufferArrivalPolicy:
  def apply(
      event: TransportOrderEvent.TransportOrderConfirmed,
      handlingUnit: HandlingUnit.PickCreated,
      at: Instant
  ): (HandlingUnit.InBuffer, HandlingUnitEvent.HandlingUnitMovedToBuffer) =
    handlingUnit.moveToBuffer(event.destination, at)
