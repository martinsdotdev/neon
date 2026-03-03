package neon.slot

import neon.common.{HandlingUnitId, OrderId, SlotId, WorkstationId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class SlotSuite extends AnyFunSpec:
  val id = SlotId()
  val workstationId = WorkstationId()
  val orderId = OrderId()
  val handlingUnitId = HandlingUnitId()
  val at = Instant.now()

  def available() = Slot.Available(id, workstationId)

  describe("Slot"):
    describe("reserving"):
      it("binds an order and handling unit to the slot"):
        val (reserved, _) = available().reserve(orderId, handlingUnitId, at)
        assert(reserved.orderId == orderId)
        assert(reserved.handlingUnitId == handlingUnitId)

      it("emits an event with the full binding"):
        val (_, event) = available().reserve(orderId, handlingUnitId, at)
        assert(event.slotId == id)
        assert(event.workstationId == workstationId)
        assert(event.orderId == orderId)
        assert(event.handlingUnitId == handlingUnitId)
        assert(event.occurredAt == at)

    describe("completing"):
      it("preserves the order and handling unit binding"):
        val (reserved, _) = available().reserve(orderId, handlingUnitId, at)
        val (completed, _) = reserved.complete(at)
        assert(completed.orderId == orderId)
        assert(completed.handlingUnitId == handlingUnitId)

      it("emits an event for consolidation group completion detection"):
        val (reserved, _) = available().reserve(orderId, handlingUnitId, at)
        val (_, event) = reserved.complete(at)
        assert(event.slotId == id)
        assert(event.workstationId == workstationId)
        assert(event.orderId == orderId)
        assert(event.handlingUnitId == handlingUnitId)
        assert(event.occurredAt == at)

    describe("releasing"):
      it("returns the slot to Available without order or handling unit"):
        val (reserved, _) = available().reserve(orderId, handlingUnitId, at)
        val (released, _) = reserved.release(at)
        assert(released.isInstanceOf[Slot.Available])

      it("carries the released order for downstream coordination"):
        val (reserved, _) = available().reserve(orderId, handlingUnitId, at)
        val (_, event) = reserved.release(at)
        assert(event.slotId == id)
        assert(event.workstationId == workstationId)
        assert(event.orderId == orderId)
        assert(event.occurredAt == at)

    describe("cycling"):
      it("accepts a new reservation after release"):
        val secondOrder = OrderId()
        val secondHU = HandlingUnitId()
        val (reserved1, _) = available().reserve(orderId, handlingUnitId, at)
        assert(reserved1.orderId == orderId)
        val (backToAvailable, _) = reserved1.release(at)
        val (reserved2, _) = backToAvailable.reserve(secondOrder, secondHU, at)
        assert(reserved2.orderId == secondOrder)
        assert(reserved2.handlingUnitId == secondHU)

    describe("identity"):
      it("carries workstation ID through all states"):
        val (reserved, _) = available().reserve(orderId, handlingUnitId, at)
        assert(reserved.workstationId == workstationId)
        val (completed, _) = reserved.complete(at)
        assert(completed.workstationId == workstationId)

      it("carries workstation ID through release cycle"):
        val (reserved, _) = available().reserve(orderId, handlingUnitId, at)
        val (released, _) = reserved.release(at)
        assert(released.workstationId == workstationId)
