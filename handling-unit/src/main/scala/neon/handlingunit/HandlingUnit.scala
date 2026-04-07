package neon.handlingunit

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{HandlingUnitId, LocationId, OrderId, PackagingLevel}

import java.time.Instant

/** Typestate-encoded aggregate for handling unit lifecycle management.
  *
  * A handling unit is a physical container that moves through the warehouse. The role is encoded in
  * the type, not a runtime field. Two independent streams share this sealed trait:
  *
  *   - '''Pick stream:''' [[HandlingUnit.PickCreated]] -> [[HandlingUnit.InBuffer]] ->
  *     [[HandlingUnit.Empty]]
  *   - '''Ship stream:''' [[HandlingUnit.ShipCreated]] -> [[HandlingUnit.Packed]] ->
  *     [[HandlingUnit.ReadyToShip]] -> [[HandlingUnit.Shipped]]
  *
  * Transitions are only available on valid source states, enforced at compile time.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait HandlingUnit:
  /** The unique identifier of this handling unit. */
  def id: HandlingUnitId

  /** The container packaging level of this handling unit. */
  def packagingLevel: PackagingLevel

/** State definitions for the [[HandlingUnit]] aggregate. */
object HandlingUnit:

  /** A pick handling unit created for transporting picked items.
    *
    * Entry state of the pick stream.
    *
    * @param id
    *   unique handling unit identifier
    * @param packagingLevel
    *   container packaging level
    * @param currentLocation
    *   current location of the handling unit
    */
  case class PickCreated(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId
  ) extends HandlingUnit:

    /** Moves this pick handling unit to a consolidation buffer, transitioning from [[PickCreated]]
      * to [[InBuffer]].
      *
      * @param locationId
      *   the buffer location
      * @param at
      *   instant of the transition
      * @return
      *   in-buffer state and moved-to-buffer event
      */
    def moveToBuffer(
        locationId: LocationId,
        at: Instant
    ): (InBuffer, HandlingUnitEvent.HandlingUnitMovedToBuffer) =
      val inBuffer = InBuffer(id, packagingLevel, locationId)
      val event = HandlingUnitEvent.HandlingUnitMovedToBuffer(id, locationId, at)
      (inBuffer, event)

  /** A ship handling unit created for outbound shipping.
    *
    * Entry state of the ship stream. Carries the order this unit fulfills.
    *
    * @param id
    *   unique handling unit identifier
    * @param packagingLevel
    *   container packaging level
    * @param currentLocation
    *   current location of the handling unit
    * @param orderId
    *   the order this ship unit fulfills
    */
  case class ShipCreated(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId,
      orderId: OrderId
  ) extends HandlingUnit:

    /** Marks this ship handling unit as packed, transitioning from [[ShipCreated]] to [[Packed]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   packed state and packing event
      */
    def pack(at: Instant): (Packed, HandlingUnitEvent.HandlingUnitPacked) =
      val packed = Packed(id, packagingLevel, currentLocation, orderId)
      val event = HandlingUnitEvent.HandlingUnitPacked(id, orderId, at)
      (packed, event)

  /** A pick handling unit that has arrived at a consolidation buffer.
    *
    * @param id
    *   unique handling unit identifier
    * @param packagingLevel
    *   container packaging level
    * @param currentLocation
    *   the buffer location
    */
  case class InBuffer(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId
  ) extends HandlingUnit:

    /** Empties this pick handling unit after deconsolidation, transitioning from [[InBuffer]] to
      * [[Empty]]. Terminal state of the pick stream.
      *
      * @param at
      *   instant of the transition
      * @return
      *   empty state and emptied event
      */
    def empty(at: Instant): (Empty, HandlingUnitEvent.HandlingUnitEmptied) =
      val empty = Empty(id, packagingLevel)
      val event = HandlingUnitEvent.HandlingUnitEmptied(id, at)
      (empty, event)

  /** A pick handling unit that has been emptied. Terminal state of the pick stream.
    *
    * @param id
    *   unique handling unit identifier
    * @param packagingLevel
    *   container packaging level
    */
  case class Empty(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel
  ) extends HandlingUnit

  /** A ship handling unit that has been packed at a workstation.
    *
    * @param id
    *   unique handling unit identifier
    * @param packagingLevel
    *   container packaging level
    * @param currentLocation
    *   current location of the handling unit
    * @param orderId
    *   the order this ship unit fulfills
    */
  case class Packed(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId,
      orderId: OrderId
  ) extends HandlingUnit:

    /** Marks this ship handling unit as ready to ship, transitioning from [[Packed]] to
      * [[ReadyToShip]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   ready-to-ship state and readiness event
      */
    def readyToShip(at: Instant): (ReadyToShip, HandlingUnitEvent.HandlingUnitReadyToShip) =
      val ready = ReadyToShip(id, packagingLevel, currentLocation, orderId)
      val event = HandlingUnitEvent.HandlingUnitReadyToShip(id, orderId, at)
      (ready, event)

  /** A ship handling unit that is ready for outbound shipping.
    *
    * @param id
    *   unique handling unit identifier
    * @param packagingLevel
    *   container packaging level
    * @param currentLocation
    *   current location of the handling unit
    * @param orderId
    *   the order this ship unit fulfills
    */
  case class ReadyToShip(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      currentLocation: LocationId,
      orderId: OrderId
  ) extends HandlingUnit:

    /** Ships this handling unit, transitioning from [[ReadyToShip]] to [[Shipped]]. Terminal state
      * of the ship stream.
      *
      * @param at
      *   instant of the transition
      * @return
      *   shipped state and shipment event
      */
    def ship(at: Instant): (Shipped, HandlingUnitEvent.HandlingUnitShipped) =
      val shipped = Shipped(id, packagingLevel, orderId)
      val event = HandlingUnitEvent.HandlingUnitShipped(id, orderId, at)
      (shipped, event)

  /** A ship handling unit that has been shipped. Terminal state of the ship stream.
    *
    * @param id
    *   unique handling unit identifier
    * @param packagingLevel
    *   container packaging level
    * @param orderId
    *   the order this ship unit fulfilled
    */
  case class Shipped(
      id: HandlingUnitId,
      packagingLevel: PackagingLevel,
      orderId: OrderId
  ) extends HandlingUnit
