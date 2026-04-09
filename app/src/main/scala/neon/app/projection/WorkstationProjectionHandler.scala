package neon.app.projection

import neon.workstation.{WorkstationActor, WorkstationEvent}
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import scala.concurrent.{ExecutionContext, Future}

/** Populates `workstation_by_type_and_state` from workstation events. */
class WorkstationProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[
      WorkstationActor.ActorEvent
    ]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[WorkstationActor.ActorEvent]
  ): Future[Done] =
    envelope.event match
      case WorkstationActor.Initialized(workstation) =>
        val stmt = session
          .createStatement(
            """INSERT INTO workstation_by_type_and_state
              |  (workstation_id, workstation_type, slot_count, state)
              |VALUES ($1, $2, $3, $4)
              |ON CONFLICT (workstation_id) DO UPDATE SET state = $4""".stripMargin
          )
          .bind(0, workstation.id.value)
          .bind(1, workstation.workstationType.toString)
          .bind(2, workstation.slotCount)
          .bind(3, workstation.getClass.getSimpleName)
        session.updateOne(stmt).map(_ => Done)

      case WorkstationActor.DomainEvent(e) =>
        val state = e match
          case _: WorkstationEvent.WorkstationEnabled  => "Idle"
          case _: WorkstationEvent.ModeSwitched        => "Idle"
          case _: WorkstationEvent.WorkstationAssigned => "Active"
          case _: WorkstationEvent.WorkstationReleased => "Idle"
          case _: WorkstationEvent.WorkstationDisabled => "Disabled"
        val stmt = session
          .createStatement(
            "UPDATE workstation_by_type_and_state SET state = $1 WHERE workstation_id = $2"
          )
          .bind(0, state)
          .bind(1, e.workstationId.value)
        session.updateOne(stmt).map(_ => Done)
