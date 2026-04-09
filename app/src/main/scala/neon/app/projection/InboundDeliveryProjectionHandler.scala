package neon.app.projection

import neon.inbounddelivery.InboundDeliveryEvent
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Populates `inbound_delivery_by_state` from inbound delivery events. */
class InboundDeliveryProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[InboundDeliveryEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[InboundDeliveryEvent]
  ): Future[Done] =
    envelope.event match
      case e: InboundDeliveryEvent.InboundDeliveryCreated =>
        val stmt = session
          .createStatement(
            """INSERT INTO inbound_delivery_by_state
              |  (inbound_delivery_id, sku_id, expected_quantity,
              |   received_quantity, rejected_quantity, state)
              |VALUES ($1, $2, $3, 0, 0, $4)
              |ON CONFLICT (inbound_delivery_id) DO UPDATE SET state = $4""".stripMargin
          )
          .bind(0, e.inboundDeliveryId.value)
          .bind(1, e.skuId.value)
          .bind(2, e.expectedQuantity)
          .bind(3, "Created")
        session.updateOne(stmt).map(_ => Done)

      case e: InboundDeliveryEvent.ReceivingStarted =>
        updateState(session, e.inboundDeliveryId.value, "Receiving")

      case e: InboundDeliveryEvent.QuantityReceived =>
        val stmt = session
          .createStatement(
            """UPDATE inbound_delivery_by_state
              |SET received_quantity = received_quantity + $1,
              |    rejected_quantity = rejected_quantity + $2
              |WHERE inbound_delivery_id = $3""".stripMargin
          )
          .bind(0, e.quantity)
          .bind(1, e.rejectedQuantity)
          .bind(2, e.inboundDeliveryId.value)
        session.updateOne(stmt).map(_ => Done)

      case e: InboundDeliveryEvent.InboundDeliveryReceived =>
        updateState(session, e.inboundDeliveryId.value, "Received")

      case e: InboundDeliveryEvent.InboundDeliveryClosed =>
        updateState(session, e.inboundDeliveryId.value, "Closed")

      case e: InboundDeliveryEvent.InboundDeliveryCancelled =>
        updateState(session, e.inboundDeliveryId.value, "Cancelled")

  private def updateState(
      session: R2dbcSession,
      inboundDeliveryId: UUID,
      state: String
  ): Future[Done] =
    val stmt = session
      .createStatement(
        "UPDATE inbound_delivery_by_state SET state = $1 WHERE inbound_delivery_id = $2"
      )
      .bind(0, state)
      .bind(1, inboundDeliveryId)
    session.updateOne(stmt).map(_ => Done)
