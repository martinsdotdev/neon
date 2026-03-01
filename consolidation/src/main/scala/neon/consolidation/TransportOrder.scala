package neon.consolidationgroup

import neon.common.{HandlingUnitId, LocationId, TransportOrderId}
import java.time.Instant

sealed trait TransportOrder:
  def id: TransportOrderId
  def handlingUnitId: HandlingUnitId
  def destination: LocationId

object TransportOrder:
  case class Pending(
      id: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId
  ) extends TransportOrder:
    def confirm(at: Instant): (Confirmed, TransportOrderEvent.TransportOrderConfirmed) =
      val confirmed = Confirmed(id, handlingUnitId, destination)
      val event =
        TransportOrderEvent.TransportOrderConfirmed(id, handlingUnitId, destination, at)
      (confirmed, event)

  case class Confirmed(
      id: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId
  ) extends TransportOrder
