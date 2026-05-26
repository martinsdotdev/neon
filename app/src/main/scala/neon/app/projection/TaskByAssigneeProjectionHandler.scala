package neon.app.projection

import neon.task.TaskEvent
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Projection handler that populates the `task_by_assignee` read-side table. Mobile operators read
  * their assigned tasks through this index — see `AsyncTaskRepository.findAssignedTo`.
  *
  * Only TaskAssigned / TaskCompleted / TaskCancelled are consumed; Created and Allocated events
  * carry no assignee. On TaskAssigned the row is upserted (assignee may have changed via a later
  * reassign); on the terminal events only the state is updated.
  */
class TaskByAssigneeProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[TaskEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[TaskEvent]
  ): Future[Done] =
    envelope.event match
      case e: TaskEvent.TaskAssigned =>
        val stmt = session.createStatement(
          """INSERT INTO task_by_assignee (task_id, user_id, state, assigned_at)
            |VALUES ($1, $2, $3, $4)
            |ON CONFLICT (task_id) DO UPDATE
            |  SET user_id = $2, state = $3, assigned_at = $4""".stripMargin
        )
        stmt.bind(0, e.taskId.value)
        stmt.bind(1, e.userId.value)
        stmt.bind(2, "Assigned")
        stmt.bind(3, OffsetDateTime.ofInstant(e.occurredAt, ZoneOffset.UTC))
        session.updateOne(stmt).map(_ => Done)

      case e: TaskEvent.TaskCompleted =>
        updateStateIfPresent(session, e.taskId.value, "Completed")

      case e: TaskEvent.TaskCancelled =>
        updateStateIfPresent(session, e.taskId.value, "Cancelled")

      case _: TaskEvent.TaskCreated | _: TaskEvent.TaskAllocated =>
        Future.successful(Done)

  private def updateStateIfPresent(
      session: R2dbcSession,
      taskId: UUID,
      state: String
  ): Future[Done] =
    val stmt = session
      .createStatement(
        "UPDATE task_by_assignee SET state = $1 WHERE task_id = $2"
      )
      .bind(0, state)
      .bind(1, taskId)
    session.updateOne(stmt).map(_ => Done)
