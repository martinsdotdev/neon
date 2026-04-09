package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.transportorder.TransportOrderEvent

import java.time.Instant

class TransportOrderProjectionHandlerSuite extends PostgresContainerSuite:

  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private val handler = TransportOrderProjectionHandler()

  describe("TransportOrderProjectionHandler"):

    it(
      "inserts into transport_order_by_handling_unit " +
        "on TransportOrderCreated"
    ):
      val transportOrderId = TransportOrderId()
      val handlingUnitId = HandlingUnitId()
      val destination = LocationId()

      val event =
        TransportOrderEvent.TransportOrderCreated(
          transportOrderId = transportOrderId,
          handlingUnitId = handlingUnitId,
          destination = destination,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"TransportOrder|${transportOrderId.value}",
              "TransportOrder"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM transport_order_by_handling_unit " +
          "WHERE transport_order_id = " +
          s"'${transportOrderId.value}'"
      )
      assert(count == 1L)

    it(
      "updates state to Confirmed " +
        "on TransportOrderConfirmed"
    ):
      val transportOrderId = TransportOrderId()
      val handlingUnitId = HandlingUnitId()
      val destination = LocationId()

      val created =
        TransportOrderEvent.TransportOrderCreated(
          transportOrderId = transportOrderId,
          handlingUnitId = handlingUnitId,
          destination = destination,
          occurredAt = Instant.now()
        )

      val confirmed =
        TransportOrderEvent.TransportOrderConfirmed(
          transportOrderId = transportOrderId,
          handlingUnitId = handlingUnitId,
          destination = destination,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"TransportOrder|${transportOrderId.value}",
              "TransportOrder"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              confirmed,
              s"TransportOrder|${transportOrderId.value}",
              "TransportOrder"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM transport_order_by_handling_unit " +
          "WHERE transport_order_id = " +
          s"'${transportOrderId.value}' " +
          "AND state = 'Confirmed'"
      )
      assert(count == 1L)

    it(
      "updates state to Cancelled " +
        "on TransportOrderCancelled"
    ):
      val transportOrderId = TransportOrderId()
      val handlingUnitId = HandlingUnitId()
      val destination = LocationId()

      val created =
        TransportOrderEvent.TransportOrderCreated(
          transportOrderId = transportOrderId,
          handlingUnitId = handlingUnitId,
          destination = destination,
          occurredAt = Instant.now()
        )

      val cancelled =
        TransportOrderEvent.TransportOrderCancelled(
          transportOrderId = transportOrderId,
          handlingUnitId = handlingUnitId,
          destination = destination,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"TransportOrder|${transportOrderId.value}",
              "TransportOrder"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              cancelled,
              s"TransportOrder|${transportOrderId.value}",
              "TransportOrder"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM transport_order_by_handling_unit " +
          "WHERE transport_order_id = " +
          s"'${transportOrderId.value}' " +
          "AND state = 'Cancelled'"
      )
      assert(count == 1L)
