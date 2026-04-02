package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, SkuId}

import java.time.Instant

/** Inventory position identified by the (location, SKU, lot) triad, tracking on-hand and reserved
  * quantities. Supports reserve/release/consume lifecycle and lot correction.
  */
case class Inventory private[neon] (
    id: InventoryId,
    locationId: LocationId,
    skuId: SkuId,
    packagingLevel: PackagingLevel,
    lot: Option[Lot],
    onHand: Int,
    reserved: Int
):
  /** Computes the quantity available for reservation (on-hand minus reserved). */
  def available: Int = onHand - reserved

  /** Reserves a quantity from available stock.
    *
    * @param quantity
    *   positive quantity to reserve; must not exceed [[available]]
    * @param at
    *   instant of the reservation
    * @return
    *   updated inventory and reservation event
    */
  def reserve(quantity: Int, at: Instant): (Inventory, InventoryEvent.InventoryReserved) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(quantity <= available, s"quantity $quantity exceeds available $available")
    val updated = copy(reserved = reserved + quantity)
    val event = InventoryEvent.InventoryReserved(id, locationId, skuId, lot, quantity, at)
    (updated, event)

  /** Releases a previously reserved quantity back to available stock.
    *
    * @param quantity
    *   positive quantity to release; must not exceed [[reserved]]
    * @param at
    *   instant of the release
    * @return
    *   updated inventory and release event
    */
  def release(quantity: Int, at: Instant): (Inventory, InventoryEvent.InventoryReleased) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(quantity <= reserved, s"quantity $quantity exceeds reserved $reserved")
    val updated = copy(reserved = reserved - quantity)
    val event = InventoryEvent.InventoryReleased(id, locationId, skuId, lot, quantity, at)
    (updated, event)

  /** Consumes a reserved quantity, reducing both on-hand and reserved.
    *
    * @param quantity
    *   positive quantity to consume; must not exceed [[reserved]]
    * @param at
    *   instant of consumption
    * @return
    *   updated inventory and consumption event
    */
  def consume(quantity: Int, at: Instant): (Inventory, InventoryEvent.InventoryConsumed) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(quantity <= reserved, s"quantity $quantity exceeds reserved $reserved")
    val updated = copy(onHand = onHand - quantity, reserved = reserved - quantity)
    val event = InventoryEvent.InventoryConsumed(id, locationId, skuId, lot, quantity, at)
    (updated, event)

  /** Corrects the lot identifier for this inventory position. Only allowed when no units are
    * reserved.
    *
    * @param newLot
    *   the corrected lot, [[None]] for untracked SKUs
    * @param at
    *   instant of the correction
    * @return
    *   updated inventory and lot correction event
    */
  def correctLot(newLot: Option[Lot], at: Instant): (Inventory, InventoryEvent.LotCorrected) =
    require(reserved == 0, s"cannot correct lot while $reserved units are reserved")
    val updated = copy(lot = newLot)
    val event = InventoryEvent.LotCorrected(id, locationId, skuId, lot, newLot, at)
    (updated, event)

/** Factory for [[Inventory]]. */
object Inventory:
  /** Creates a new inventory position identified by the (location, SKU, lot) triad.
    *
    * @param locationId
    *   storage location
    * @param skuId
    *   stock-keeping unit
    * @param packagingLevel
    *   the packaging level of the inventory
    * @param lot
    *   lot identifier, [[None]] for untracked SKUs
    * @param onHand
    *   initial quantity on hand; must be non-negative
    * @param at
    *   instant of creation
    * @return
    *   inventory record and creation event
    */
  def create(
      locationId: LocationId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lot: Option[Lot],
      onHand: Int,
      at: Instant
  ): (Inventory, InventoryEvent.InventoryCreated) =
    require(onHand >= 0, s"onHand must be non-negative, got $onHand")
    val id = InventoryId()
    val inventory = Inventory(id, locationId, skuId, packagingLevel, lot, onHand, reserved = 0)
    val event =
      InventoryEvent.InventoryCreated(id, locationId, skuId, packagingLevel, lot, onHand, at)
    (inventory, event)
