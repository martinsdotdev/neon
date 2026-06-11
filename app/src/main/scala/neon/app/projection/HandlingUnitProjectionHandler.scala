package neon.app.projection

import neon.handlingunit.HandlingUnitProjectionSchema.HandlingUnitLookup
import neon.handlingunit.{HandlingUnitActor, HandlingUnitEvent}
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import scala.concurrent.{ExecutionContext, Future}

/** Populates `handling_unit_lookup` from handling unit events. */
class HandlingUnitProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[
      HandlingUnitActor.ActorEvent
    ]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[HandlingUnitActor.ActorEvent]
  ): Future[Done] =
    envelope.event match
      case HandlingUnitActor.Initialized(handlingUnit) =>
        val stmt = session
          .createStatement(HandlingUnitLookup.Upsert)
          .bind(0, handlingUnit.id.value)
          .bind(1, handlingUnit.packagingLevel.toString)
          .bindNull(2, classOf[java.util.UUID])
          .bind(3, handlingUnit.getClass.getSimpleName)
        session.updateOne(stmt).map(_ => Done)

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
          .createStatement(HandlingUnitLookup.UpdateStateAndCurrentLocation)
          .bind(0, state)
        location match
          case Some(loc) => stmt.bind(1, loc)
          case None      => stmt.bindNull(1, classOf[java.util.UUID])
        stmt.bind(2, e.handlingUnitId.value)
        session.updateOne(stmt).map(_ => Done)
