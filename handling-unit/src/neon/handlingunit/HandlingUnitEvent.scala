package neon.handlingunit

import neon.common.{HandlingUnitId, LocationId, OrderId}

import java.time.Instant

sealed trait HandlingUnitEvent:
  def handlingUnitId: HandlingUnitId
  def occurredAt: Instant

object HandlingUnitEvent:
  case class HandlingUnitMovedToBuffer(
      handlingUnitId: HandlingUnitId,
      locationId: LocationId,
      occurredAt: Instant
  ) extends HandlingUnitEvent

  case class HandlingUnitEmptied(
      handlingUnitId: HandlingUnitId,
      occurredAt: Instant
  ) extends HandlingUnitEvent

  case class HandlingUnitPacked(
      handlingUnitId: HandlingUnitId,
      orderId: OrderId,
      occurredAt: Instant
  ) extends HandlingUnitEvent

  case class HandlingUnitReadyToShip(
      handlingUnitId: HandlingUnitId,
      orderId: OrderId,
      occurredAt: Instant
  ) extends HandlingUnitEvent

  case class HandlingUnitShipped(
      handlingUnitId: HandlingUnitId,
      orderId: OrderId,
      occurredAt: Instant
  ) extends HandlingUnitEvent
