package neon.transportorder

import neon.common.{HandlingUnitId, LocationId, TransportOrderId}

import java.time.Instant

/** Domain events emitted by [[TransportOrder]] state transitions. */
sealed trait TransportOrderEvent:
  /** The transport order that emitted this event. */
  def transportOrderId: TransportOrderId

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for [[TransportOrder]]. */
object TransportOrderEvent:
  /** Emitted when a new transport order is created in [[TransportOrder.Pending]] state.
    *
    * @param transportOrderId
    *   the transport order identifier
    * @param handlingUnitId
    *   the handling unit to transport
    * @param destination
    *   target location for delivery
    * @param occurredAt
    *   instant of creation
    */
  case class TransportOrderCreated(
      transportOrderId: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId,
      occurredAt: Instant
  ) extends TransportOrderEvent

  /** Emitted when an operator confirms delivery at the destination.
    *
    * @param transportOrderId
    *   the transport order identifier
    * @param handlingUnitId
    *   the handling unit delivered
    * @param destination
    *   the confirmed delivery location
    * @param occurredAt
    *   instant of confirmation
    */
  case class TransportOrderConfirmed(
      transportOrderId: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId,
      occurredAt: Instant
  ) extends TransportOrderEvent

  /** Emitted when a pending transport order is cancelled.
    *
    * @param transportOrderId
    *   the transport order identifier
    * @param handlingUnitId
    *   the handling unit whose transport was cancelled
    * @param destination
    *   the intended delivery location
    * @param occurredAt
    *   instant of cancellation
    */
  case class TransportOrderCancelled(
      transportOrderId: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId,
      occurredAt: Instant
  ) extends TransportOrderEvent
