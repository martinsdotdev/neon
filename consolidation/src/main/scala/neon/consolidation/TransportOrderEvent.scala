package neon.consolidationgroup

import neon.common.{HandlingUnitId, LocationId, TransportOrderId}
import java.time.Instant

sealed trait TransportOrderEvent:
  def transportOrderId: TransportOrderId
  def occurredAt: Instant

object TransportOrderEvent:
  case class TransportOrderConfirmed(
      transportOrderId: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId,
      occurredAt: Instant
  ) extends TransportOrderEvent
