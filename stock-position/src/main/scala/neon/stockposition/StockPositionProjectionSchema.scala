package neon.stockposition

/** Read-side projection schema for the stock position module: the single owner of the table and
  * column names shared by [[PekkoStockPositionRepository]] queries and the app-side
  * `StockPositionProjectionHandler`. DDL:
  * app/src/main/resources/db/V7__stock_inbound_cycle_count_tables.sql.
  */
object StockPositionProjectionSchema:

  /** Stock positions indexed by (SKU, warehouse area), for
    * [[AsyncStockPositionRepository.findBySkuAndArea]].
    */
  object StockPositionBySkuArea:
    val Table = "stock_position_by_sku_area"
    val StockPositionId = "stock_position_id"
    val SkuId = "sku_id"
    val WarehouseAreaId = "warehouse_area_id"
    val Status = "status"
    val ExpirationDate = "expiration_date"
    val OnHandQuantity = "on_hand_quantity"
    val AvailableQuantity = "available_quantity"
    val AllocatedQuantity = "allocated_quantity"
    val ReservedQuantity = "reserved_quantity"
    val BlockedQuantity = "blocked_quantity"

    val SelectStockPositionIdsBySkuAndArea =
      s"SELECT $StockPositionId FROM $Table WHERE $SkuId = $$1 AND $WarehouseAreaId = $$2"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($StockPositionId, $SkuId, $WarehouseAreaId, $Status,
         |   $ExpirationDate, $OnHandQuantity, $AvailableQuantity,
         |   $AllocatedQuantity, $ReservedQuantity, $BlockedQuantity)
         |VALUES ($$1, $$2, $$3, $$4, $$5, $$6, $$6, 0, 0, 0)
         |ON CONFLICT ($StockPositionId) DO UPDATE SET
         |  $OnHandQuantity = $$6, $AvailableQuantity = $$6""".stripMargin

    /** Builds the quantity-mutation statement around one of the set clauses below; `$1` binds the
      * quantity and `$2` the stock position id.
      */
    def updateQuantitiesStatement(setClause: String): String =
      s"UPDATE $Table SET $setClause WHERE $StockPositionId = $$2"

    val AllocateSetClause =
      s"$AvailableQuantity = $AvailableQuantity - $$1," +
        s" $AllocatedQuantity = $AllocatedQuantity + $$1"

    val DeallocateSetClause =
      s"$AvailableQuantity = $AvailableQuantity + $$1," +
        s" $AllocatedQuantity = $AllocatedQuantity - $$1"

    val AddQuantitySetClause =
      s"$OnHandQuantity = $OnHandQuantity + $$1, $AvailableQuantity = $AvailableQuantity + $$1"

    val ConsumeAllocatedSetClause =
      s"$OnHandQuantity = $OnHandQuantity - $$1, $AllocatedQuantity = $AllocatedQuantity - $$1"

    val ReserveSetClause =
      s"$AvailableQuantity = $AvailableQuantity - $$1, $ReservedQuantity = $ReservedQuantity + $$1"

    val ReleaseReservationSetClause =
      s"$AvailableQuantity = $AvailableQuantity + $$1, $ReservedQuantity = $ReservedQuantity - $$1"

    val BlockSetClause =
      s"$AvailableQuantity = $AvailableQuantity - $$1, $BlockedQuantity = $BlockedQuantity + $$1"

    val UnblockSetClause =
      s"$AvailableQuantity = $AvailableQuantity + $$1, $BlockedQuantity = $BlockedQuantity - $$1"

    val AdjustQuantities =
      s"""UPDATE $Table
         |SET $OnHandQuantity = $OnHandQuantity + $$1,
         |    $AvailableQuantity = $AvailableQuantity + $$1
         |WHERE $StockPositionId = $$2""".stripMargin

    val UpdateStatus =
      s"UPDATE $Table SET $Status = $$1 WHERE $StockPositionId = $$2"
