package neon.task

/** Read-side projection schema for the task module: the single owner of the table and column names
  * shared by [[PekkoTaskRepository]] queries and the app-side projection handlers
  * (`TaskProjectionHandler`, `TaskByAssigneeProjectionHandler`). DDL:
  * app/src/main/resources/db/V5__read_side_projections.sql (task_by_assignee:
  * V8__task_by_assignee_projection.sql).
  */
object TaskProjectionSchema:

  /** Tasks indexed by wave, for [[AsyncTaskRepository.findByWaveId]]. */
  object TaskByWave:
    val Table = "task_by_wave"
    val TaskId = "task_id"
    val WaveId = "wave_id"
    val OrderId = "order_id"
    val HandlingUnitId = "handling_unit_id"
    val State = "state"

    val SelectTaskIdsByWaveId =
      s"SELECT $TaskId FROM $Table WHERE $WaveId = $$1"

    val Upsert =
      s"""INSERT INTO $Table ($TaskId, $WaveId, $OrderId, $HandlingUnitId, $State)
         |VALUES ($$1, $$2, $$3, $$4, $$5)
         |ON CONFLICT ($TaskId) DO UPDATE SET $State = $$5""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $TaskId = $$2"

  /** Tasks indexed by handling unit, for [[AsyncTaskRepository.findByHandlingUnitId]]. */
  object TaskByHandlingUnit:
    val Table = "task_by_handling_unit"
    val TaskId = "task_id"
    val HandlingUnitId = "handling_unit_id"
    val WaveId = "wave_id"
    val OrderId = "order_id"
    val State = "state"

    val SelectTaskIdsByHandlingUnitId =
      s"SELECT $TaskId FROM $Table WHERE $HandlingUnitId = $$1"

    val Upsert =
      s"""INSERT INTO $Table ($TaskId, $HandlingUnitId, $WaveId, $OrderId, $State)
         |VALUES ($$1, $$2, $$3, $$4, $$5)
         |ON CONFLICT ($TaskId) DO UPDATE SET $State = $$5""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $TaskId = $$2"

  /** Tasks indexed by assignee, for [[AsyncTaskRepository.findAssignedTo]]. */
  object TaskByAssignee:
    val Table = "task_by_assignee"
    val TaskId = "task_id"
    val UserId = "user_id"
    val State = "state"
    val AssignedAt = "assigned_at"

    val SelectTaskIdsByUserId =
      s"SELECT $TaskId FROM $Table WHERE $UserId = $$1"

    val SelectTaskIdsByUserIdAndState =
      s"SELECT $TaskId FROM $Table WHERE $UserId = $$1 AND $State = $$2"

    val Upsert =
      s"""INSERT INTO $Table ($TaskId, $UserId, $State, $AssignedAt)
         |VALUES ($$1, $$2, $$3, $$4)
         |ON CONFLICT ($TaskId) DO UPDATE
         |  SET $UserId = $$2, $State = $$3, $AssignedAt = $$4""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $TaskId = $$2"
