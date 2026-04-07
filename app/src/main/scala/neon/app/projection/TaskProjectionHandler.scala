package neon.app.projection

import neon.task.TaskEvent
import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}

import scala.concurrent.{ExecutionContext, Future}

/** Projection handler that consumes task events and populates the `task_by_wave` and
  * `task_by_handling_unit` read-side tables.
  */
class TaskProjectionHandler(using ExecutionContext) extends R2dbcHandler[EventEnvelope[TaskEvent]]:

  override def process(
      session: R2dbcSession,
      envelope: EventEnvelope[TaskEvent]
  ): Future[org.apache.pekko.Done] =
    envelope.event match
      case e: TaskEvent.TaskCreated =>
        val stmt = session
          .createStatement(
            """INSERT INTO task_by_wave (task_id, wave_id, order_id, handling_unit_id, state)
              |VALUES ($1, $2, $3, $4, $5)
              |ON CONFLICT (task_id) DO UPDATE SET state = $5""".stripMargin
          )
          .bind(0, e.taskId.value)
          .bind(1, e.waveId.map(_.value).orNull)
          .bind(2, e.orderId.value)
          .bind(3, e.handlingUnitId.map(_.value).orNull)
          .bind(4, "Planned")

        val stmt2 = e.handlingUnitId match
          case Some(huId) =>
            Some(
              session
                .createStatement(
                  """INSERT INTO task_by_handling_unit (task_id, handling_unit_id, wave_id, order_id, state)
                    |VALUES ($1, $2, $3, $4, $5)
                    |ON CONFLICT (task_id) DO UPDATE SET state = $5""".stripMargin
                )
                .bind(0, e.taskId.value)
                .bind(1, huId.value)
                .bind(2, e.waveId.map(_.value).orNull)
                .bind(3, e.orderId.value)
                .bind(4, "Planned")
            )
          case None => None

        session.updateOne(stmt).flatMap { _ =>
          stmt2 match
            case Some(s) => session.updateOne(s).map(_ => org.apache.pekko.Done)
            case None    => Future.successful(org.apache.pekko.Done)
        }

      case e: TaskEvent.TaskAllocated =>
        updateState(session, e.taskId.value, "Allocated")

      case e: TaskEvent.TaskAssigned =>
        updateState(session, e.taskId.value, "Assigned")

      case e: TaskEvent.TaskCompleted =>
        updateState(session, e.taskId.value, "Completed")

      case e: TaskEvent.TaskCancelled =>
        updateState(session, e.taskId.value, "Cancelled")

  private def updateState(
      session: R2dbcSession,
      taskId: java.util.UUID,
      state: String
  ): Future[org.apache.pekko.Done] =
    val stmt1 = session
      .createStatement(
        "UPDATE task_by_wave SET state = $1 WHERE task_id = $2"
      )
      .bind(0, state)
      .bind(1, taskId)
    val stmt2 = session
      .createStatement(
        "UPDATE task_by_handling_unit SET state = $1 WHERE task_id = $2"
      )
      .bind(0, state)
      .bind(1, taskId)
    session.updateOne(stmt1).flatMap(_ => session.updateOne(stmt2)).map(_ => org.apache.pekko.Done)
