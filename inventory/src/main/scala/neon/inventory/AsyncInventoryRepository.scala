package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, SkuId}

import scala.concurrent.Future

/** Async port trait for [[Inventory]] aggregate persistence and queries. */
trait AsyncInventoryRepository:
  def findById(id: InventoryId): Future[Option[Inventory]]
  def findByLocationSkuLot(
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot]
  ): Future[Option[Inventory]]
  def save(inventory: Inventory, event: InventoryEvent): Future[Unit]
