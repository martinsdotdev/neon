package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, SkuId}

/** Port trait for Inventory aggregate persistence and queries. */
trait InventoryRepository:
  def findById(id: InventoryId): Option[Inventory]
  def findByLocationSkuLot(
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot]
  ): Option[Inventory]
  def save(inventory: Inventory, event: InventoryEvent): Unit
