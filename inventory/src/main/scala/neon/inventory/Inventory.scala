package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, SkuId}

import java.time.Instant

case class Inventory private (
    id: InventoryId,
    locationId: LocationId,
    skuId: SkuId,
    packagingLevel: PackagingLevel,
    lot: Option[Lot],
    onHand: Int,
    reserved: Int
):
  def available: Int = onHand - reserved

  def reserve(qty: Int, at: Instant): (Inventory, InventoryEvent.InventoryReserved) =
    require(qty > 0, s"qty must be positive, got $qty")
    require(qty <= available, s"qty $qty exceeds available $available")
    val updated = copy(reserved = reserved + qty)
    val event = InventoryEvent.InventoryReserved(id, locationId, skuId, lot, qty, at)
    (updated, event)

  def release(qty: Int, at: Instant): (Inventory, InventoryEvent.InventoryReleased) =
    require(qty > 0, s"qty must be positive, got $qty")
    require(qty <= reserved, s"qty $qty exceeds reserved $reserved")
    val updated = copy(reserved = reserved - qty)
    val event = InventoryEvent.InventoryReleased(id, locationId, skuId, lot, qty, at)
    (updated, event)

  def consume(qty: Int, at: Instant): (Inventory, InventoryEvent.InventoryConsumed) =
    require(qty > 0, s"qty must be positive, got $qty")
    require(qty <= reserved, s"qty $qty exceeds reserved $reserved")
    val updated = copy(onHand = onHand - qty, reserved = reserved - qty)
    val event = InventoryEvent.InventoryConsumed(id, locationId, skuId, lot, qty, at)
    (updated, event)

  def correctLot(newLot: Option[Lot], at: Instant): (Inventory, InventoryEvent.LotCorrected) =
    require(reserved == 0, s"cannot correct lot while $reserved units are reserved")
    val updated = copy(lot = newLot)
    val event = InventoryEvent.LotCorrected(id, locationId, skuId, lot, newLot, at)
    (updated, event)

object Inventory:
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
