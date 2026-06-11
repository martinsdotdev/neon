package neon.app.projection

import neon.counttask.CountTaskEvent
import neon.counttask.CountTaskProjectionSchema.CountTaskByCycleCount
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Populates `count_task_by_cycle_count` from count task events. */
class CountTaskProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[CountTaskEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[CountTaskEvent]
  ): Future[Done] =
    envelope.event match
      case e: CountTaskEvent.CountTaskCreated =>
        val stmt = session
          .createStatement(CountTaskByCycleCount.Upsert)
          .bind(0, e.countTaskId.value)
          .bind(1, e.cycleCountId.value)
          .bind(2, e.skuId.value)
          .bind(3, e.locationId.value)
          .bind(4, e.expectedQuantity)
          .bind(5, "Pending")
        session.updateOne(stmt).map(_ => Done)

      case e: CountTaskEvent.CountTaskAssigned =>
        updateState(session, e.countTaskId.value, "Assigned")

      case e: CountTaskEvent.CountTaskRecorded =>
        val stmt = session
          .createStatement(CountTaskByCycleCount.UpdateCountResult)
          .bind(0, e.actualQuantity)
          .bind(1, e.variance)
          .bind(2, "Recorded")
          .bind(3, e.countTaskId.value)
        session.updateOne(stmt).map(_ => Done)

      case e: CountTaskEvent.CountTaskCancelled =>
        updateState(session, e.countTaskId.value, "Cancelled")

  private def updateState(
      session: R2dbcSession,
      countTaskId: UUID,
      state: String
  ): Future[Done] =
    val stmt = session
      .createStatement(CountTaskByCycleCount.UpdateState)
      .bind(0, state)
      .bind(1, countTaskId)
    session.updateOne(stmt).map(_ => Done)
