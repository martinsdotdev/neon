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

      it("consolidation group contains all orders from the wave"):
        val (consolidationGroup, _) = results.head
        assert(consolidationGroup.orderIds == orderIds)
        assert(consolidationGroup.waveId == waveId)

      it("emits a creation event for downstream coordination"):
        val (consolidationGroup, event) = results.head
        assert(event.groupId == consolidationGroup.id)
        assert(event.orderIds == orderIds)
        assert(event.occurredAt == at)

    describe("Single-order wave"):
      it("produces no consolidation groups"):
        val results = ConsolidationGroupFormationPolicy(released(OrderGrouping.Single), at)
        assert(results.isEmpty)
