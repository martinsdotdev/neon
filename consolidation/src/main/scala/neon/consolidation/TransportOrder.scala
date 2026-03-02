package neon.consolidationgroup

import neon.common.{HandlingUnitId, LocationId, TransportOrderId}
import java.time.Instant

sealed trait TransportOrder:
  def id: TransportOrderId
  def handlingUnitId: HandlingUnitId
  def destination: LocationId

object TransportOrder:
  def create(
      handlingUnitId: HandlingUnitId,
      destination: LocationId,
      at: Instant
  ): (Pending, TransportOrderEvent.TransportOrderCreated) =
    val id = TransportOrderId()
    val pending = Pending(id, handlingUnitId, destination)
    val event = TransportOrderEvent.TransportOrderCreated(id, handlingUnitId, destination, at)
    (pending, event)

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

    def cancel(at: Instant): (Cancelled, TransportOrderEvent.TransportOrderCancelled) =
      val cancelled = Cancelled(id, handlingUnitId, destination)
      val event =
        TransportOrderEvent.TransportOrderCancelled(id, handlingUnitId, destination, at)
      (cancelled, event)

  case class Confirmed(
      id: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId
  ) extends TransportOrder

  case class Cancelled(
      id: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId
  ) extends TransportOrder
