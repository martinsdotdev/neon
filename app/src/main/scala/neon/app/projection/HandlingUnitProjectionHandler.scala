package neon.app.projection

import neon.handlingunit.{HandlingUnitActor, HandlingUnitEvent}
import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}

import scala.concurrent.{ExecutionContext, Future}

/** Populates `handling_unit_lookup` from handling unit events. */
class HandlingUnitProjectionHandler(using ExecutionContext)
    extends R2dbcHandler[EventEnvelope[HandlingUnitActor.ActorEvent]]:

  override def process(
      session: R2dbcSession,
      envelope: EventEnvelope[HandlingUnitActor.ActorEvent]
  ): Future[org.apache.pekko.Done] =
    envelope.event match
      case HandlingUnitActor.Initialized(hu) =>
        val stmt = session
          .createStatement(
            """INSERT INTO handling_unit_lookup
              |  (handling_unit_id, packaging_level, current_location, state)
              |VALUES ($1, $2, $3, $4)
              |ON CONFLICT (handling_unit_id) DO UPDATE
              |  SET state = $4, current_location = $3""".stripMargin
          )
          .bind(0, hu.id.value)
          .bind(1, hu.packagingLevel.toString)
          .bindNull(2, classOf[java.util.UUID])
          .bind(3, hu.getClass.getSimpleName)
        session.updateOne(stmt).map(_ => org.apache.pekko.Done)

      case HandlingUnitActor.DomainEvent(e) =>
        val (state, location) = e match
          case e: HandlingUnitEvent.HandlingUnitMovedToBuffer =>
            ("InBuffer", Some(e.locationId.value))
          case _: HandlingUnitEvent.HandlingUnitEmptied =>
            ("Empty", None)
          case _: HandlingUnitEvent.HandlingUnitPacked =>
            ("Packed", None)
          case _: HandlingUnitEvent.HandlingUnitReadyToShip =>
            ("ReadyToShip", None)
          case _: HandlingUnitEvent.HandlingUnitShipped =>
            ("Shipped", None)
        val stmt = session
          .createStatement(
            "UPDATE handling_unit_lookup SET state = $1, current_location = $2 WHERE handling_unit_id = $3"
          )
          .bind(0, state)
        location match
          case Some(loc) => stmt.bind(1, loc)
          case None      => stmt.bindNull(1, classOf[java.util.UUID])
        stmt.bind(2, e.handlingUnitId.value)
        session.updateOne(stmt).map(_ => org.apache.pekko.Done)
