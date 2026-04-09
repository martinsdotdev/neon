package neon.app.projection

import neon.cyclecount.CycleCountEvent
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Populates `cycle_count_by_state` from cycle count events. */
class CycleCountProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[CycleCountEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[CycleCountEvent]
  ): Future[Done] =
    envelope.event match
      case e: CycleCountEvent.CycleCountCreated =>
        val stmt = session
          .createStatement(
            """INSERT INTO cycle_count_by_state
              |  (cycle_count_id, warehouse_area_id, count_type, count_method, state)
              |VALUES ($1, $2, $3, $4, $5)
              |ON CONFLICT (cycle_count_id) DO UPDATE SET state = $5""".stripMargin
          )
          .bind(0, e.cycleCountId.value)
          .bind(1, e.warehouseAreaId.value)
          .bind(2, e.countType.toString)
          .bind(3, e.countMethod.toString)
          .bind(4, "New")
        session.updateOne(stmt).map(_ => Done)

      case e: CycleCountEvent.CycleCountStarted =>
        updateState(session, e.cycleCountId.value, "InProgress")

      case e: CycleCountEvent.CycleCountCompleted =>
        updateState(session, e.cycleCountId.value, "Completed")

      case e: CycleCountEvent.CycleCountCancelled =>
        updateState(session, e.cycleCountId.value, "Cancelled")

  private def updateState(
      session: R2dbcSession,
      cycleCountId: UUID,
      state: String
  ): Future[Done] =
    val stmt = session
      .createStatement(
        "UPDATE cycle_count_by_state SET state = $1 WHERE cycle_count_id = $2"
      )
      .bind(0, state)
      .bind(1, cycleCountId)
    session.updateOne(stmt).map(_ => Done)
