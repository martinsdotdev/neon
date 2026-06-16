package neon.workstation

/** Read-side projection schema for the workstation module: the single owner of the table and column
  * names shared by [[PekkoWorkstationRepository]] queries and the app-side
  * `WorkstationProjectionHandler`. DDL: app/src/main/resources/db/V5__read_side_projections.sql
  * (`mode` column added by V7, currently unused in Scala SQL).
  */
object WorkstationProjectionSchema:

  /** Workstations indexed by type and state, for [[AsyncWorkstationRepository.findIdleByType]]. */
  object WorkstationByTypeAndState:
    val Table = "workstation_by_type_and_state"
    val WorkstationId = "workstation_id"
    val WorkstationType = "workstation_type"
    val SlotCount = "slot_count"
    val State = "state"

    val SelectIdleWorkstationIdByType =
      s"SELECT $WorkstationId FROM $Table WHERE $WorkstationType = $$1 AND $State = 'Idle' LIMIT 1"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($WorkstationId, $WorkstationType, $SlotCount, $State)
         |VALUES ($$1, $$2, $$3, $$4)
         |ON CONFLICT ($WorkstationId) DO UPDATE SET $State = $$4""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $WorkstationId = $$2"
