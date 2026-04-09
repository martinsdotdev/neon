package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.slot.{Slot, SlotActor, SlotEvent}

import java.time.Instant

class SlotProjectionHandlerSuite
    extends PostgresContainerSuite:

  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private val handler = SlotProjectionHandler()

  describe("SlotProjectionHandler"):

    it(
      "inserts into slot_by_workstation on Initialized"
    ):
      val slotId = SlotId()
      val workstationId = WorkstationId()
      val slot = Slot.Available(
        id = slotId,
        workstationId = workstationId
      )

      val event = SlotActor.Initialized(slot)

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"Slot|${slotId.value}",
              "Slot"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM slot_by_workstation " +
          s"WHERE slot_id = '${slotId.value}'"
      )
      assert(count == 1L)

    it("updates state to Reserved on SlotReserved"):
      val slotId = SlotId()
      val workstationId = WorkstationId()
      val orderId = OrderId()

      val slot = Slot.Available(
        id = slotId,
        workstationId = workstationId
      )

      val initialized = SlotActor.Initialized(slot)
      val reserved = SlotActor.DomainEvent(
        SlotEvent.SlotReserved(
          slotId = slotId,
          workstationId = workstationId,
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
              s"Slot|${slotId.value}",
              "Slot"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              reserved,
              s"Slot|${slotId.value}",
              "Slot"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM slot_by_workstation " +
          s"WHERE slot_id = '${slotId.value}' " +
          "AND state = 'Reserved'"
      )
      assert(count == 1L)

    it("updates state to Available on SlotReleased"):
      val slotId = SlotId()
      val workstationId = WorkstationId()
      val orderId = OrderId()

      val slot = Slot.Available(
        id = slotId,
        workstationId = workstationId
      )

      val initialized = SlotActor.Initialized(slot)
      val reserved = SlotActor.DomainEvent(
        SlotEvent.SlotReserved(
          slotId = slotId,
          workstationId = workstationId,
          orderId = orderId,
          handlingUnitId = HandlingUnitId(),
          occurredAt = Instant.now()
        )
      )
      val released = SlotActor.DomainEvent(
        SlotEvent.SlotReleased(
          slotId = slotId,
          workstationId = workstationId,
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
              s"Slot|${slotId.value}",
              "Slot"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              reserved,
              s"Slot|${slotId.value}",
              "Slot"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              released,
              s"Slot|${slotId.value}",
              "Slot"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM slot_by_workstation " +
          s"WHERE slot_id = '${slotId.value}' " +
          "AND state = 'Available'"
      )
      assert(count == 1L)
