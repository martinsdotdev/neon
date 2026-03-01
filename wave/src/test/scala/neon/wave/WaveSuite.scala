package neon.wave

import neon.common.{OrderId, WaveId}
import org.scalatest.funspec.AnyFunSpec

class WaveSuite extends AnyFunSpec:
  val id = WaveId()
  val orderIds = List(OrderId(), OrderId(), OrderId())

  def planned(grouping: OrderGrouping = OrderGrouping.Multi) =
    Wave.Planned(id, grouping, orderIds)

  describe("Wave"):
    describe("releasing"):
      it("authorizes work to begin"):
        val (_, event) = planned().release()
        assert(event.waveId == id)
        assert(event.orderGrouping == OrderGrouping.Multi)

      it("carries order IDs for task creation"):
        val (_, event) = planned().release()
        assert(event.orderIds == orderIds)

    describe("completing"):
      it("marks all work as done"):
        val (released, _) = planned().release()
        val (_, event) = released.complete()
        assert(event.waveId == id)

    describe("cancelling"):
      it("can be cancelled before release"):
        val (_, event) = planned(OrderGrouping.Single).cancel()
        assert(event.waveId == id)

      it("can be cancelled after release"):
        val (released, _) = planned().release()
        val (_, event) = released.cancel()
        assert(event.waveId == id)

    describe("order grouping"):
      it("is carried in events for downstream routing"):
        val (_, releaseEvent) = planned(OrderGrouping.Multi).release()
        assert(releaseEvent.orderGrouping == OrderGrouping.Multi)
        val (released, _) = planned(OrderGrouping.Single).release()
        val (_, completeEvent) = released.complete()
        assert(completeEvent.orderGrouping == OrderGrouping.Single)
