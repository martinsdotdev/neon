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
    describe("when orders include cancellable states"):
      it("cancels pending orders"):
        val orders = List(pendingOrder(), confirmed())
        val results = TransportOrderCancellationPolicy(orders, at)
        assert(results.size == 1)
        assert(results.head._1.id == orders.head.id)

      it("skips confirmed orders"):
        val orders = List(confirmed(), pendingOrder())
        val results = TransportOrderCancellationPolicy(orders, at)
        assert(results.size == 1)
        assert(results.head._1.id == orders.last.id)

      it("skips already cancelled orders"):
        val orders = List(pendingOrder(), cancelled())
        val results = TransportOrderCancellationPolicy(orders, at)
        assert(results.size == 1)
        assert(results.head._1.id == orders.head.id)

      it("emits one event per cancelled order"):
        val orders = List(pendingOrder(), pendingOrder(), confirmed())
        val results = TransportOrderCancellationPolicy(orders, at)
        assert(results.size == 2)
        results.foreach: (cancelled, event) =>
          assert(event.occurredAt == at)
          assert(event.transportOrderId == cancelled.id)

    describe("event fields"):
      it("carries handlingUnitId and destination for audit"):
        val orders = List(pendingOrder())
        val (_, event) = TransportOrderCancellationPolicy(orders, at).head
        assert(event.handlingUnitId == handlingUnitId)
        assert(event.destination == destination)

    describe("state preservation"):
      it("preserves handlingUnitId and destination in cancelled state"):
        val orders = List(pendingOrder())
        val (cancelled, _) = TransportOrderCancellationPolicy(orders, at).head
        assert(cancelled.handlingUnitId == handlingUnitId)
        assert(cancelled.destination == destination)

    describe("when all orders are already terminal"):
      it("produces no cancellations"):
        val orders = List(confirmed(), cancelled())
        assert(TransportOrderCancellationPolicy(orders, at).isEmpty)

    describe("when the list is empty"):
      it("produces no cancellations"):
        assert(TransportOrderCancellationPolicy(List.empty, at).isEmpty)
