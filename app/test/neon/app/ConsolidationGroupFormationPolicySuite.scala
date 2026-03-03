package neon.app

import neon.common.{OrderId, WaveId}
import neon.wave.{OrderGrouping, WaveEvent}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class ConsolidationGroupFormationPolicySuite extends AnyFunSpec:
  val waveId = WaveId()
  val orderIds = List(OrderId(), OrderId(), OrderId())
  val at = Instant.now()

  def released(grouping: OrderGrouping) =
    WaveEvent.WaveReleased(waveId, grouping, orderIds, at)

  describe("ConsolidationGroupFormationPolicy"):
    describe("Multi-order wave"):
      val results = ConsolidationGroupFormationPolicy(released(OrderGrouping.Multi), at)

      it("creates a consolidation group for the wave"):
        assert(results.size == 1)

      it("group contains all orders from the wave"):
        val (group, _) = results.head
        assert(group.orderIds == orderIds)
        assert(group.waveId == waveId)

      it("emits a creation event for downstream coordination"):
        val (group, event) = results.head
        assert(event.groupId == group.id)
        assert(event.orderIds == orderIds)
        assert(event.occurredAt == at)

    describe("Single-order wave"):
      it("produces no consolidation groups"):
        val results = ConsolidationGroupFormationPolicy(released(OrderGrouping.Single), at)
        assert(results.isEmpty)
