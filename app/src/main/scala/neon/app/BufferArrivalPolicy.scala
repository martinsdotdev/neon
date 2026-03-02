package neon.app

import neon.consolidationgroup.{HandlingUnit, HandlingUnitEvent, TransportOrderEvent}
import java.time.Instant

object BufferArrivalPolicy:
  def evaluate(
      event: TransportOrderEvent.TransportOrderConfirmed,
      handlingUnit: HandlingUnit.PickCreated,
      at: Instant
  ): (HandlingUnit.InBuffer, HandlingUnitEvent.HandlingUnitMovedToBuffer) =
    handlingUnit.moveToBuffer(event.destination, at)
