package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.transportorder.TransportOrderEvent
import org.apache.pekko.persistence.query.TimestampOffset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext

import java.time.Instant

class TransportOrderProjectionHandlerSuite extends PostgresContainerSuite:

  private given ExecutionContext = system.executionContext

  private val handler = TransportOrderProjectionHandler()

  private def withSession(
      f: R2dbcSession => Unit
  ): Unit =
    val connection =
      Mono.from(connectionFactory.create()).block()
    try
      val session = new R2dbcSession(connection)(using
        system.executionContext,
        system
      )
      f(session)
    finally Mono.from(connection.close()).block()

  private def envelope[E](
      event: E,
      persistenceId: String
  ): EventEnvelope[E] =
    new EventEnvelope[E](
      offset = TimestampOffset.Zero,
      persistenceId = persistenceId,
      sequenceNr = 1L,
      eventOption = Some(event),
      timestamp = System.currentTimeMillis(),
      eventMetadata = None,
      entityType = "TransportOrder",
      slice = 0
    )

  describe("TransportOrderProjectionHandler") {

    it(
      "should insert into transport_order_by_handling_unit on TransportOrderCreated"
    ) {
      val toId = TransportOrderId()
      val huId = HandlingUnitId()
      val destination = LocationId()

      val event =
        TransportOrderEvent.TransportOrderCreated(
          transportOrderId = toId,
          handlingUnitId = huId,
          destination = destination,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"TransportOrder|${toId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM transport_order_by_handling_unit WHERE transport_order_id = '${toId.value}'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Confirmed on TransportOrderConfirmed"
    ) {
      val toId = TransportOrderId()
      val huId = HandlingUnitId()
      val destination = LocationId()

      val created =
        TransportOrderEvent.TransportOrderCreated(
          transportOrderId = toId,
          handlingUnitId = huId,
          destination = destination,
          occurredAt = Instant.now()
        )

      val confirmed =
        TransportOrderEvent.TransportOrderConfirmed(
          transportOrderId = toId,
          handlingUnitId = huId,
          destination = destination,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"TransportOrder|${toId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              confirmed,
              s"TransportOrder|${toId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM transport_order_by_handling_unit WHERE transport_order_id = '${toId.value}' AND state = 'Confirmed'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Cancelled on TransportOrderCancelled"
    ) {
      val toId = TransportOrderId()
      val huId = HandlingUnitId()
      val destination = LocationId()

      val created =
        TransportOrderEvent.TransportOrderCreated(
          transportOrderId = toId,
          handlingUnitId = huId,
          destination = destination,
          occurredAt = Instant.now()
        )

      val cancelled =
        TransportOrderEvent.TransportOrderCancelled(
          transportOrderId = toId,
          handlingUnitId = huId,
          destination = destination,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"TransportOrder|${toId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              cancelled,
              s"TransportOrder|${toId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM transport_order_by_handling_unit WHERE transport_order_id = '${toId.value}' AND state = 'Cancelled'"
      )
      assert(count == 1L)
    }
  }
