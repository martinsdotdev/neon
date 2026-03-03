package neon.handlingunit

import neon.common.{HandlingUnitId, LocationId, OrderId, PackagingLevel}

import java.time.Instant

sealed trait HandlingUnit:
  def id: HandlingUnitId
  def packagingLevel: PackagingLevel

object HandlingUnit:
  case class PickCreated(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId
  ) extends HandlingUnit:
    def moveToBuffer(
        locationId: LocationId,
        at: Instant
    ): (InBuffer, HandlingUnitEvent.HandlingUnitMovedToBuffer) =
      val inBuffer = InBuffer(id, packagingLevel, locationId)
      val event = HandlingUnitEvent.HandlingUnitMovedToBuffer(id, locationId, at)
      (inBuffer, event)

  case class ShipCreated(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId,
      orderId: OrderId
  ) extends HandlingUnit:
    def pack(at: Instant): (Packed, HandlingUnitEvent.HandlingUnitPacked) =
      val packed = Packed(id, packagingLevel, currentLocation, orderId)
      val event = HandlingUnitEvent.HandlingUnitPacked(id, orderId, at)
      (packed, event)

  case class InBuffer(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId
  ) extends HandlingUnit:
    def empty(at: Instant): (Empty, HandlingUnitEvent.HandlingUnitEmptied) =
      val empty = Empty(id, packagingLevel)
      val event = HandlingUnitEvent.HandlingUnitEmptied(id, at)
      (empty, event)

  case class Empty(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel
  ) extends HandlingUnit

  case class Packed(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId,
      orderId: OrderId
  ) extends HandlingUnit:
    def readyToShip(at: Instant): (ReadyToShip, HandlingUnitEvent.HandlingUnitReadyToShip) =
      val ready = ReadyToShip(id, packagingLevel, currentLocation, orderId)
      val event = HandlingUnitEvent.HandlingUnitReadyToShip(id, orderId, at)
      (ready, event)

  case class ReadyToShip(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId,
      orderId: OrderId
  ) extends HandlingUnit:
    def ship(at: Instant): (Shipped, HandlingUnitEvent.HandlingUnitShipped) =
      val shipped = Shipped(id, packagingLevel, orderId)
      val event = HandlingUnitEvent.HandlingUnitShipped(id, orderId, at)
      (shipped, event)

  case class Shipped(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      orderId: OrderId
  ) extends HandlingUnit
