package neon.app.projection

import neon.transportorder.TransportOrderEvent
import neon.transportorder.TransportOrderProjectionSchema.TransportOrderByHandlingUnit
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import scala.concurrent.{ExecutionContext, Future}

/** Populates `transport_order_by_handling_unit` from transport order events. */
class TransportOrderProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[TransportOrderEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[TransportOrderEvent]
  ): Future[Done] =
    envelope.event match
      case e: TransportOrderEvent.TransportOrderCreated =>
        val stmt = session
          .createStatement(TransportOrderByHandlingUnit.Upsert)
          .bind(0, e.transportOrderId.value)
          .bind(1, e.handlingUnitId.value)
          .bind(2, e.destination.value)
          .bind(3, "Pending")
        session.updateOne(stmt).map(_ => Done)

      case e: TransportOrderEvent.TransportOrderConfirmed =>
        val stmt = session
          .createStatement(TransportOrderByHandlingUnit.UpdateState)
          .bind(0, "Confirmed")
          .bind(1, e.transportOrderId.value)
        session.updateOne(stmt).map(_ => Done)

      case e: TransportOrderEvent.TransportOrderCancelled =>
        val stmt = session
          .createStatement(TransportOrderByHandlingUnit.UpdateState)
          .bind(0, "Cancelled")
          .bind(1, e.transportOrderId.value)
        session.updateOne(stmt).map(_ => Done)
