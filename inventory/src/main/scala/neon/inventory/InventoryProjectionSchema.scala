package neon.inventory

/** Read-side projection schema for the inventory module: the single owner of the table and column
  * names shared by [[PekkoInventoryRepository]] queries and the app-side
  * `InventoryProjectionHandler`. DDL: app/src/main/resources/db/V5__read_side_projections.sql.
  */
object InventoryProjectionSchema:

  /** Inventory indexed by the (location, sku, lot) triad, for
    * [[AsyncInventoryRepository.findByLocationSkuLot]].
    */
  object InventoryByLocationSkuLot:
    val Table = "inventory_by_location_sku_lot"
    val InventoryId = "inventory_id"
    val LocationId = "location_id"
    val SkuId = "sku_id"
    val Lot = "lot"
    val OnHand = "on_hand"
    val Reserved = "reserved"

    val SelectInventoryIdByLocationSkuLot =
      s"SELECT $InventoryId FROM $Table" +
        s" WHERE $LocationId = $$1 AND $SkuId = $$2 AND $Lot IS NOT DISTINCT FROM $$3"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($InventoryId, $LocationId, $SkuId, $Lot, $OnHand, $Reserved)
         |VALUES ($$1, $$2, $$3, $$4, $$5, 0)
         |ON CONFLICT ($InventoryId) DO UPDATE SET $OnHand = $$5""".stripMargin

    val IncrementReserved =
      s"UPDATE $Table SET $Reserved = $Reserved + $$1 WHERE $InventoryId = $$2"

    val DecrementReserved =
      s"UPDATE $Table SET $Reserved = $Reserved - $$1 WHERE $InventoryId = $$2"

    val DecrementOnHandAndReserved =
      s"UPDATE $Table SET $OnHand = $OnHand - $$1, $Reserved = $Reserved - $$1" +
        s" WHERE $InventoryId = $$2"

    val UpdateLot =
      s"UPDATE $Table SET $Lot = $$1 WHERE $InventoryId = $$2"
