package neon.wave

import neon.common.{OrderId, WaveId}
import org.scalatest.funspec.AnyFunSpec
import java.time.Instant

class WaveSuite extends AnyFunSpec:
  val id = WaveId()
  val orderIds = List(OrderId(), OrderId(), OrderId())
  val at = Instant.now()

  def planned(grouping: OrderGrouping = OrderGrouping.Multi) =
    Wave.Planned(id, grouping, orderIds)

  describe("Wave"):
    describe("releasing"):
      it("authorizes work to begin"):
        val (_, event) = planned().release(at)
        assert(event.waveId == id)
        assert(event.orderGrouping == OrderGrouping.Multi)

      it("carries order IDs for task creation"):
        val (_, event) = planned().release(at)
        assert(event.orderIds == orderIds)

      it("stamps the event with the given instant"):
        val (_, event) = planned().release(at)
        assert(event.occurredAt == at)

    describe("completing"):
      it("marks all work as done"):
        val (released, _) = planned().release(at)
        val (completed, event) = released.complete(at)
        assert(completed.isInstanceOf[Wave.Completed])
        assert(event.waveId == id)

    describe("cancelling"):
      it("can be cancelled before release"):
        val (cancelled, event) = planned(OrderGrouping.Single).cancel(at)
        assert(cancelled.isInstanceOf[Wave.Cancelled])
        assert(event.waveId == id)

      it("can be cancelled after release"):
        val (released, _) = planned().release(at)
        val (cancelled, event) = released.cancel(at)
        assert(cancelled.isInstanceOf[Wave.Cancelled])
        assert(event.waveId == id)

      it("cancellation event carries orderGrouping for downstream routing"):
        val (_, event) = planned(OrderGrouping.Single).cancel(at)
        assert(event.orderGrouping == OrderGrouping.Single)
        val (released, _) = planned(OrderGrouping.Multi).release(at)
        val (_, releasedCancelEvent) = released.cancel(at)
        assert(releasedCancelEvent.orderGrouping == OrderGrouping.Multi)

    describe("order grouping"):
      it("is carried in events for downstream routing"):
        val (_, releaseEvent) = planned(OrderGrouping.Multi).release(at)
        assert(releaseEvent.orderGrouping == OrderGrouping.Multi)
        val (released, _) = planned(OrderGrouping.Single).release(at)
        val (_, completeEvent) = released.complete(at)
        assert(completeEvent.orderGrouping == OrderGrouping.Single)
