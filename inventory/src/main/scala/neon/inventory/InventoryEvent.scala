package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, SkuId}

import java.time.Instant

/** Domain events emitted by [[Inventory]] state changes. */
sealed trait InventoryEvent:
  /** The inventory position that emitted this event. */
  def inventoryId: InventoryId

  /** The storage location of the inventory position. */
  def locationId: LocationId

  /** The stock-keeping unit of the inventory position. */
  def skuId: SkuId

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for [[Inventory]]. */
object InventoryEvent:
  /** Emitted when a new inventory position is created.
    *
    * @param inventoryId
    *   the inventory identifier
    * @param locationId
    *   storage location
    * @param skuId
    *   stock-keeping unit
    * @param packagingLevel
    *   the packaging level of the inventory
    * @param lot
    *   lot identifier, [[None]] for untracked SKUs
    * @param onHand
    *   initial quantity on hand
    * @param occurredAt
    *   instant of creation
    */
  case class InventoryCreated(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lot: Option[Lot],
      onHand: Int,
      occurredAt: Instant
  ) extends InventoryEvent

  /** Emitted when a quantity is reserved from available stock.
    *
    * @param inventoryId
    *   the inventory identifier
    * @param locationId
    *   storage location
    * @param skuId
    *   stock-keeping unit
    * @param lot
    *   lot identifier
    * @param quantityReserved
    *   the quantity reserved
    * @param occurredAt
    *   instant of the reservation
    */
  case class InventoryReserved(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot],
      quantityReserved: Int,
      occurredAt: Instant
  ) extends InventoryEvent

  /** Emitted when a previously reserved quantity is released back to available.
    *
    * @param inventoryId
    *   the inventory identifier
    * @param locationId
    *   storage location
    * @param skuId
    *   stock-keeping unit
    * @param lot
    *   lot identifier
    * @param quantityReleased
    *   the quantity released
    * @param occurredAt
    *   instant of the release
    */
  case class InventoryReleased(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot],
      quantityReleased: Int,
      occurredAt: Instant
  ) extends InventoryEvent

  /** Emitted when a reserved quantity is consumed, reducing both on-hand and reserved.
    *
    * @param inventoryId
    *   the inventory identifier
    * @param locationId
    *   storage location
    * @param skuId
    *   stock-keeping unit
    * @param lot
    *   lot identifier
    * @param quantityConsumed
    *   the quantity consumed
    * @param occurredAt
    *   instant of consumption
    */
  case class InventoryConsumed(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot],
      quantityConsumed: Int,
      occurredAt: Instant
  ) extends InventoryEvent

  /** Emitted when the lot identifier of an inventory position is corrected.
    *
    * @param inventoryId
    *   the inventory identifier
    * @param locationId
    *   storage location
    * @param skuId
    *   stock-keeping unit
    * @param previousLot
    *   the lot before correction
    * @param newLot
    *   the lot after correction
    * @param occurredAt
    *   instant of the correction
    */
  case class LotCorrected(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      previousLot: Option[Lot],
      newLot: Option[Lot],
      occurredAt: Instant
  ) extends InventoryEvent
