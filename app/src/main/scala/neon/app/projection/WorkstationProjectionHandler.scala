package neon.app.projection

import neon.workstation.{WorkstationActor, WorkstationEvent}
import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}

import scala.concurrent.{ExecutionContext, Future}

/** Populates `workstation_by_type_and_state` from workstation events. */
class WorkstationProjectionHandler(using ExecutionContext)
    extends R2dbcHandler[EventEnvelope[WorkstationActor.ActorEvent]]:

  override def process(
      session: R2dbcSession,
      envelope: EventEnvelope[WorkstationActor.ActorEvent]
  ): Future[org.apache.pekko.Done] =
    envelope.event match
      case WorkstationActor.Initialized(ws) =>
        val stmt = session
          .createStatement(
            """INSERT INTO workstation_by_type_and_state
              |  (workstation_id, workstation_type, slot_count, state)
              |VALUES ($1, $2, $3, $4)
              |ON CONFLICT (workstation_id) DO UPDATE SET state = $4""".stripMargin
          )
          .bind(0, ws.id.value)
          .bind(1, ws.workstationType.toString)
          .bind(2, ws.slotCount)
          .bind(3, ws.getClass.getSimpleName)
        session.updateOne(stmt).map(_ => org.apache.pekko.Done)

      case WorkstationActor.DomainEvent(e) =>
        val state = e match
          case _: WorkstationEvent.WorkstationEnabled  => "Idle"
          case _: WorkstationEvent.WorkstationAssigned => "Active"
          case _: WorkstationEvent.WorkstationReleased => "Idle"
          case _: WorkstationEvent.WorkstationDisabled => "Disabled"
        val stmt = session
          .createStatement(
            "UPDATE workstation_by_type_and_state SET state = $1 WHERE workstation_id = $2"
          )
          .bind(0, state)
          .bind(1, e.workstationId.value)
        session.updateOne(stmt).map(_ => org.apache.pekko.Done)
