package neon.handlingunit

import neon.common.{HandlingUnitId, LocationId, OrderId}

import java.time.Instant

/** Domain events emitted by [[HandlingUnit]] state transitions. */
sealed trait HandlingUnitEvent:
  /** The handling unit that emitted this event. */
  def handlingUnitId: HandlingUnitId

  /** The instant at which this event occurred. */
  def occurredAt: Instant

/** Event case classes for the [[HandlingUnit]] aggregate. */
object HandlingUnitEvent:

  /** Emitted when a pick handling unit arrives at a consolidation buffer. */
  case class HandlingUnitMovedToBuffer(
      handlingUnitId: HandlingUnitId,
      locationId: LocationId,
      occurredAt: Instant
  ) extends HandlingUnitEvent

  /** Emitted when a pick handling unit is emptied after deconsolidation. */
  case class HandlingUnitEmptied(
      handlingUnitId: HandlingUnitId,
      occurredAt: Instant
  ) extends HandlingUnitEvent

  /** Emitted when a ship handling unit is packed at a workstation. */
  case class HandlingUnitPacked(
      handlingUnitId: HandlingUnitId,
      orderId: OrderId,
      occurredAt: Instant
  ) extends HandlingUnitEvent

  /** Emitted when a ship handling unit is marked ready for outbound shipping.
    */
  case class HandlingUnitReadyToShip(
      handlingUnitId: HandlingUnitId,
      orderId: OrderId,
      occurredAt: Instant
  ) extends HandlingUnitEvent

  /** Emitted when a ship handling unit has been shipped. */
  case class HandlingUnitShipped(
      handlingUnitId: HandlingUnitId,
      orderId: OrderId,
      occurredAt: Instant
  ) extends HandlingUnitEvent
