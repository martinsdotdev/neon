package neon.handlingunitstock

/** Read-side projection schema for the handling unit stock module: the single owner of the table
  * and column names shared by [[PekkoHandlingUnitStockRepository]] queries and the app-side
  * `HandlingUnitStockProjectionHandler`. DDL:
  * app/src/main/resources/db/V7__stock_inbound_cycle_count_tables.sql.
  */
object HandlingUnitStockProjectionSchema:

  /** Handling unit stock indexed by container, for
    * [[AsyncHandlingUnitStockRepository.findByContainer]].
    */
  object HandlingUnitStockByContainer:
    val Table = "handling_unit_stock_by_container"
    val HandlingUnitStockId = "handling_unit_stock_id"
    val SkuId = "sku_id"
    val StockPositionId = "stock_position_id"
    val ContainerId = "container_id"
    val SlotCode = "slot_code"
    val OnHandQuantity = "on_hand_quantity"
    val AvailableQuantity = "available_quantity"
    val AllocatedQuantity = "allocated_quantity"
    val ReservedQuantity = "reserved_quantity"
    val BlockedQuantity = "blocked_quantity"

    val SelectHandlingUnitStockIdsByContainerId =
      s"SELECT $HandlingUnitStockId FROM $Table WHERE $ContainerId = $$1"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($HandlingUnitStockId, $SkuId, $StockPositionId, $ContainerId,
         |   $SlotCode, $OnHandQuantity, $AvailableQuantity,
         |   $AllocatedQuantity, $ReservedQuantity, $BlockedQuantity)
         |VALUES ($$1, $$2, $$3, $$4, $$5, $$6, $$6, 0, 0, 0)
         |ON CONFLICT ($HandlingUnitStockId) DO UPDATE SET
         |  $OnHandQuantity = $$6, $AvailableQuantity = $$6""".stripMargin

    /** Builds the quantity-mutation statement around one of the set clauses below; `$1` binds the
      * quantity and `$2` the handling unit stock id.
      */
    def updateQuantitiesStatement(setClause: String): String =
      s"UPDATE $Table SET $setClause WHERE $HandlingUnitStockId = $$2"

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
         |WHERE $HandlingUnitStockId = $$2""".stripMargin
