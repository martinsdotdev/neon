package neon.cyclecount

/** Read-side projection schema for the cycle count module: the single owner of the table and column
  * names used by the app-side `CycleCountProjectionHandler`. The module's Pekko repository does not
  * query this table; the schema lives here so the module owns its read-model vocabulary. DDL:
  * app/src/main/resources/db/V7__stock_inbound_cycle_count_tables.sql.
  */
object CycleCountProjectionSchema:

  /** Cycle counts indexed by state. */
  object CycleCountByState:
    val Table = "cycle_count_by_state"
    val CycleCountId = "cycle_count_id"
    val WarehouseAreaId = "warehouse_area_id"
    val CountType = "count_type"
    val CountMethod = "count_method"
    val State = "state"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($CycleCountId, $WarehouseAreaId, $CountType, $CountMethod, $State)
         |VALUES ($$1, $$2, $$3, $$4, $$5)
         |ON CONFLICT ($CycleCountId) DO UPDATE SET $State = $$5""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $CycleCountId = $$2"
