package neon.consolidationgroup

import neon.common.{HandlingUnitId, LocationId, TransportOrderId}
import org.scalatest.funspec.AnyFunSpec
import java.time.Instant

class TransportOrderSuite extends AnyFunSpec:
  val id = TransportOrderId()
  val handlingUnitId = HandlingUnitId()
  val destination = LocationId()
  val at = Instant.now()

  def aPending() = TransportOrder.Pending(id, handlingUnitId, destination)

  describe("TransportOrder"):
    describe("confirming"):
      it("records that the operator delivered the container"):
        val (confirmed, _) = aPending().confirm(at)
        assert(confirmed.isInstanceOf[TransportOrder.Confirmed])

      it("carries handling unit and destination for audit"):
        val (confirmed, _) = aPending().confirm(at)
        assert(confirmed.handlingUnitId == handlingUnitId)
        assert(confirmed.destination == destination)

      it("emits an event for downstream routing policies"):
        val (_, event) = aPending().confirm(at)
        assert(event.transportOrderId == id)
        assert(event.handlingUnitId == handlingUnitId)
        assert(event.destination == destination)
        assert(event.occurredAt == at)
