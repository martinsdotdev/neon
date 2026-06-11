package neon.slot

/** Read-side projection schema for the slot module: the single owner of the table and column names
  * shared by [[PekkoSlotRepository]] queries and the app-side `SlotProjectionHandler`. DDL:
  * app/src/main/resources/db/V5__read_side_projections.sql.
  */
object SlotProjectionSchema:

  /** Slots indexed by workstation, for [[AsyncSlotRepository.findByWorkstationId]]. */
  object SlotByWorkstation:
    val Table = "slot_by_workstation"
    val SlotId = "slot_id"
    val WorkstationId = "workstation_id"
    val OrderId = "order_id"
    val State = "state"

    val SelectSlotIdsByWorkstationId =
      s"SELECT $SlotId FROM $Table WHERE $WorkstationId = $$1"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($SlotId, $WorkstationId, $OrderId, $State)
         |VALUES ($$1, $$2, $$3, $$4)
         |ON CONFLICT ($SlotId) DO UPDATE SET $State = $$4""".stripMargin

    val UpdateOrderIdAndState =
      s"UPDATE $Table SET $OrderId = $$1, $State = $$2 WHERE $SlotId = $$3"

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $SlotId = $$2"

    val ClearOrderIdAndUpdateState =
      s"UPDATE $Table SET $OrderId = NULL, $State = $$1 WHERE $SlotId = $$2"
