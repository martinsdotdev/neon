package neon.app.projection

import neon.slot.{SlotActor, SlotEvent}
import org.apache.pekko.Done
import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import scala.concurrent.{ExecutionContext, Future}

/** Populates `slot_by_workstation` from slot events. */
class SlotProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[SlotActor.ActorEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[SlotActor.ActorEvent]
  ): Future[Done] =
    envelope.event match
      case SlotActor.Initialized(slot) =>
        val stmt = session
          .createStatement(
            """INSERT INTO slot_by_workstation
              |  (slot_id, workstation_id, order_id, state)
              |VALUES ($1, $2, $3, $4)
              |ON CONFLICT (slot_id) DO UPDATE SET state = $4""".stripMargin
          )
          .bind(0, slot.id.value)
          .bind(1, slot.workstationId.value)
          .bindNull(2, classOf[java.util.UUID])
          .bind(3, "Available")
        session.updateOne(stmt).map(_ => Done)

      case SlotActor.DomainEvent(e) =>
        e match
          case e: SlotEvent.SlotReserved =>
            val stmt = session
              .createStatement(
                "UPDATE slot_by_workstation SET order_id = $1, state = $2 WHERE slot_id = $3"
              )
              .bind(0, e.orderId.value)
              .bind(1, "Reserved")
              .bind(2, e.slotId.value)
            session.updateOne(stmt).map(_ => Done)

          case e: SlotEvent.SlotCompleted =>
            val stmt = session
              .createStatement(
                "UPDATE slot_by_workstation SET state = $1 WHERE slot_id = $2"
              )
              .bind(0, "Completed")
              .bind(1, e.slotId.value)
            session.updateOne(stmt).map(_ => Done)

          case e: SlotEvent.SlotReleased =>
            val stmt = session
              .createStatement(
                "UPDATE slot_by_workstation SET order_id = NULL, state = $1 WHERE slot_id = $2"
              )
              .bind(0, "Available")
              .bind(1, e.slotId.value)
            session.updateOne(stmt).map(_ => Done)
