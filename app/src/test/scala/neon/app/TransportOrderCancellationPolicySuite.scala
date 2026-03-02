package neon.app

import neon.common.{HandlingUnitId, LocationId, TransportOrderId}
import neon.transportorder.TransportOrder
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TransportOrderCancellationPolicySuite extends AnyFunSpec:
  val handlingUnitId = HandlingUnitId()
  val destination = LocationId()
  val at = Instant.now()

  def pendingOrder() = TransportOrder.Pending(TransportOrderId(), handlingUnitId, destination)

  def confirmed() = TransportOrder.Confirmed(TransportOrderId(), handlingUnitId, destination)

  def cancelled() = TransportOrder.Cancelled(TransportOrderId(), handlingUnitId, destination)

  describe("TransportOrderCancellationPolicy"):
    describe("when transport orders include pending"):
      it("cancels pending transport orders"):
        val orders = List(pendingOrder(), confirmed())
        val results = TransportOrderCancellationPolicy.evaluate(orders, at)
        assert(results.size == 1)
        assert(results.head._1.id == orders.head.id)

      it("skips confirmed transport orders"):
        val orders = List(confirmed(), pendingOrder())
        val results = TransportOrderCancellationPolicy.evaluate(orders, at)
        assert(results.size == 1)
        assert(results.head._1.id == orders.last.id)

      it("skips already cancelled transport orders"):
        val orders = List(pendingOrder(), cancelled())
        val results = TransportOrderCancellationPolicy.evaluate(orders, at)
        assert(results.size == 1)
        assert(results.head._1.id == orders.head.id)

      it("emits one event per cancellation"):
        val orders = List(pendingOrder(), pendingOrder(), confirmed())
        val results = TransportOrderCancellationPolicy.evaluate(orders, at)
        assert(results.size == 2)
        results.foreach: (cancelled, event) =>
          assert(event.occurredAt == at)
          assert(event.transportOrderId == cancelled.id)

      it("carries handlingUnitId and destination in the event"):
        val orders = List(pendingOrder())
        val (_, event) = TransportOrderCancellationPolicy.evaluate(orders, at).head
        assert(event.handlingUnitId == handlingUnitId)
        assert(event.destination == destination)

    describe("when all transport orders are already terminal"):
      it("produces no cancellations"):
        val orders = List(confirmed(), cancelled())
        assert(TransportOrderCancellationPolicy.evaluate(orders, at).isEmpty)

    describe("when the list is empty"):
      it("produces no cancellations"):
        assert(TransportOrderCancellationPolicy.evaluate(List.empty, at).isEmpty)
