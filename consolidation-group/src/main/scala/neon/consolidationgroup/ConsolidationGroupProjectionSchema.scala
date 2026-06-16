package neon.consolidationgroup

/** Read-side projection schema for the consolidation group module: the single owner of the table
  * and column names shared by [[PekkoConsolidationGroupRepository]] queries and the app-side
  * `ConsolidationGroupProjectionHandler`. DDL:
  * app/src/main/resources/db/V5__read_side_projections.sql.
  */
object ConsolidationGroupProjectionSchema:

  /** Consolidation groups indexed by wave, for [[AsyncConsolidationGroupRepository.findByWaveId]].
    */
  object ConsolidationGroupByWave:
    val Table = "consolidation_group_by_wave"
    val ConsolidationGroupId = "consolidation_group_id"
    val WaveId = "wave_id"
    val OrderIds = "order_ids"
    val State = "state"

    val SelectConsolidationGroupIdsByWaveId =
      s"SELECT $ConsolidationGroupId FROM $Table WHERE $WaveId = $$1"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($ConsolidationGroupId, $WaveId, $OrderIds, $State)
         |VALUES ($$1, $$2, $$3, $$4)
         |ON CONFLICT ($ConsolidationGroupId) DO UPDATE SET $State = $$4""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $ConsolidationGroupId = $$2"
