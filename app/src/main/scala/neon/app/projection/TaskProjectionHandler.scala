package neon.app.projection

import neon.task.TaskEvent

import org.apache.pekko.Done
import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Projection handler that consumes task events and populates the `task_by_wave` and
  * `task_by_handling_unit` read-side tables.
  */
class TaskProjectionHandler(using ExecutionContext) extends R2dbcHandler[EventEnvelope[TaskEvent]]:

  override def process(
      session: R2dbcSession,
      envelope: EventEnvelope[TaskEvent]
  ): Future[Done] =
    envelope.event match
      case e: TaskEvent.TaskCreated =>
        val stmt = session.createStatement(
          """INSERT INTO task_by_wave (task_id, wave_id, order_id, handling_unit_id, state)
            |VALUES ($1, $2, $3, $4, $5)
            |ON CONFLICT (task_id) DO UPDATE SET state = $5""".stripMargin
        )
        stmt.bind(0, e.taskId.value)
        bindOptionalUuid(stmt, 1, e.waveId.map(_.value))
        stmt.bind(2, e.orderId.value)
        bindOptionalUuid(stmt, 3, e.handlingUnitId.map(_.value))
        stmt.bind(4, "Planned")

        val insertHandlingUnit = e.handlingUnitId.map { handlingUnitId =>
          val stmt2 = session.createStatement(
            """INSERT INTO task_by_handling_unit (task_id, handling_unit_id, wave_id, order_id, state)
              |VALUES ($1, $2, $3, $4, $5)
              |ON CONFLICT (task_id) DO UPDATE SET state = $5""".stripMargin
          )
          stmt2.bind(0, e.taskId.value)
          stmt2.bind(1, handlingUnitId.value)
          bindOptionalUuid(stmt2, 2, e.waveId.map(_.value))
          stmt2.bind(3, e.orderId.value)
          stmt2.bind(4, "Planned")
          stmt2
        }

        session.updateOne(stmt).flatMap { _ =>
          insertHandlingUnit match
            case Some(s) => session.updateOne(s).map(_ => Done)
            case None    => Future.successful(Done)
        }

      case e: TaskEvent.TaskAllocated =>
        updateState(session, e.taskId.value, "Allocated")
      case e: TaskEvent.TaskAssigned =>
        updateState(session, e.taskId.value, "Assigned")
      case e: TaskEvent.TaskCompleted =>
        updateState(session, e.taskId.value, "Completed")
      case e: TaskEvent.TaskCancelled =>
        updateState(session, e.taskId.value, "Cancelled")

  private def bindOptionalUuid(
      stmt: io.r2dbc.spi.Statement,
      index: Int,
      value: Option[UUID]
  ): Unit =
    value match
      case Some(v) => stmt.bind(index, v)
      case None    => stmt.bindNull(index, classOf[UUID])

  private def updateState(
      session: R2dbcSession,
      taskId: UUID,
      state: String
  ): Future[Done] =
    val stmt1 = session
      .createStatement("UPDATE task_by_wave SET state = $1 WHERE task_id = $2")
      .bind(0, state)
      .bind(1, taskId)
    val stmt2 = session
      .createStatement(
        "UPDATE task_by_handling_unit SET state = $1 WHERE task_id = $2"
      )
      .bind(0, state)
      .bind(1, taskId)
    session
      .updateOne(stmt1)
      .flatMap(_ => session.updateOne(stmt2))
      .map(_ => Done)
