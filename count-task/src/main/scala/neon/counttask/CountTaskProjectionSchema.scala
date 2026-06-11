package neon.counttask

/** Read-side projection schema for the count task module: the single owner of the table and column
  * names shared by [[PekkoCountTaskRepository]] queries and the app-side
  * `CountTaskProjectionHandler`. DDL:
  * app/src/main/resources/db/V7__stock_inbound_cycle_count_tables.sql.
  */
object CountTaskProjectionSchema:

  /** Count tasks indexed by cycle count, for [[AsyncCountTaskRepository.findByCycleCountId]]. */
  object CountTaskByCycleCount:
    val Table = "count_task_by_cycle_count"
    val CountTaskId = "count_task_id"
    val CycleCountId = "cycle_count_id"
    val SkuId = "sku_id"
    val LocationId = "location_id"
    val ExpectedQuantity = "expected_quantity"
    val ActualQuantity = "actual_quantity"
    val Variance = "variance"
    val State = "state"

    val SelectCountTaskIdsByCycleCountId =
      s"SELECT $CountTaskId FROM $Table WHERE $CycleCountId = $$1"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($CountTaskId, $CycleCountId, $SkuId, $LocationId,
         |   $ExpectedQuantity, $ActualQuantity, $Variance, $State)
         |VALUES ($$1, $$2, $$3, $$4, $$5, NULL, NULL, $$6)
         |ON CONFLICT ($CountTaskId) DO UPDATE SET $State = $$6""".stripMargin

    val UpdateCountResult =
      s"""UPDATE $Table
         |SET $ActualQuantity = $$1, $Variance = $$2, $State = $$3
         |WHERE $CountTaskId = $$4""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $CountTaskId = $$2"
