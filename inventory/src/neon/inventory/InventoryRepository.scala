package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, SkuId}

/** Port trait for [[Inventory]] aggregate persistence and queries. */
trait InventoryRepository:
  /** Finds an inventory position by its unique identifier.
    *
    * @param id
    *   the inventory identifier
    * @return
    *   the inventory position if it exists, [[None]] otherwise
    */
  def findById(id: InventoryId): Option[Inventory]

  /** Finds an inventory position by the (location, SKU, lot) triad.
    *
    * @param locationId
    *   storage location
    * @param skuId
    *   stock-keeping unit
    * @param lot
    *   lot identifier, [[None]] for untracked SKUs
    * @return
    *   the inventory position if it exists, [[None]] otherwise
    */
  def findByLocationSkuLot(
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot]
  ): Option[Inventory]

  /** Persists an inventory position along with the event that caused the state change.
    *
    * @param inventory
    *   the inventory position to persist
    * @param event
    *   the domain event to store
    */
  def save(inventory: Inventory, event: InventoryEvent): Unit
