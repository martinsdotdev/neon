package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.slot.{Slot, SlotActor, SlotEvent}
import org.apache.pekko.persistence.query.TimestampOffset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext

import java.time.Instant

class SlotProjectionHandlerSuite extends PostgresContainerSuite:

  private given ExecutionContext = system.executionContext

  private val handler = SlotProjectionHandler()

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
      entityType = "Slot",
      slice = 0
    )

  describe("SlotProjectionHandler") {

    it(
      "should insert into slot_by_workstation on Initialized"
    ) {
      val slotId = SlotId()
      val wsId = WorkstationId()
      val slot = Slot.Available(
        id = slotId,
        workstationId = wsId
      )

      val event = SlotActor.Initialized(slot)

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"Slot|${slotId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM slot_by_workstation WHERE slot_id = '${slotId.value}'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Reserved on SlotReserved"
    ) {
      val slotId = SlotId()
      val wsId = WorkstationId()
      val orderId = OrderId()

      val slot = Slot.Available(
        id = slotId,
        workstationId = wsId
      )

      val initialized = SlotActor.Initialized(slot)
      val reserved = SlotActor.DomainEvent(
        SlotEvent.SlotReserved(
          slotId = slotId,
          workstationId = wsId,
          orderId = orderId,
          handlingUnitId = HandlingUnitId(),
          occurredAt = Instant.now()
        )
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              initialized,
              s"Slot|${slotId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              reserved,
              s"Slot|${slotId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM slot_by_workstation WHERE slot_id = '${slotId.value}' AND state = 'Reserved'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Available on SlotReleased"
    ) {
      val slotId = SlotId()
      val wsId = WorkstationId()
      val orderId = OrderId()

      val slot = Slot.Available(
        id = slotId,
        workstationId = wsId
      )

      val initialized = SlotActor.Initialized(slot)
      val reserved = SlotActor.DomainEvent(
        SlotEvent.SlotReserved(
          slotId = slotId,
          workstationId = wsId,
          orderId = orderId,
          handlingUnitId = HandlingUnitId(),
          occurredAt = Instant.now()
        )
      )
      val released = SlotActor.DomainEvent(
        SlotEvent.SlotReleased(
          slotId = slotId,
          workstationId = wsId,
          orderId = orderId,
          occurredAt = Instant.now()
        )
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              initialized,
              s"Slot|${slotId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              reserved,
              s"Slot|${slotId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              released,
              s"Slot|${slotId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM slot_by_workstation WHERE slot_id = '${slotId.value}' AND state = 'Available'"
      )
      assert(count == 1L)
    }
  }
