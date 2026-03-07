package neon.core

import neon.common.{ConsolidationGroupId, OrderId, WaveId, WorkstationId}
import neon.consolidationgroup.ConsolidationGroup
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class ConsolidationGroupCancellationPolicySuite extends AnyFunSpec:
  val waveId = WaveId()
  val orderIds = List(OrderId(), OrderId())
  val workstationId = WorkstationId()
  val at = Instant.now()

  def created() = ConsolidationGroup.Created(ConsolidationGroupId(), waveId, orderIds)

  def picked() = ConsolidationGroup.Picked(ConsolidationGroupId(), waveId, orderIds)

  def readyForWorkstation() = ConsolidationGroup.ReadyForWorkstation(ConsolidationGroupId(), waveId, orderIds)

  def assigned() = ConsolidationGroup.Assigned(ConsolidationGroupId(), waveId, orderIds, workstationId)

  def completed() = ConsolidationGroup.Completed(ConsolidationGroupId(), waveId, orderIds, workstationId)

  def cancelled() = ConsolidationGroup.Cancelled(ConsolidationGroupId(), waveId, orderIds)

  describe("ConsolidationGroupCancellationPolicy"):
    describe("when groups include non-terminal states"):
      it("cancels Created groups"):
        val groups = List(created(), completed())
        val results = ConsolidationGroupCancellationPolicy(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.head.id)

      it("cancels Picked groups"):
        val groups = List(picked(), completed())
        val results = ConsolidationGroupCancellationPolicy(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.head.id)

      it("cancels ReadyForWorkstation groups"):
        val groups = List(readyForWorkstation(), completed())
        val results = ConsolidationGroupCancellationPolicy(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.head.id)

      it("cancels Assigned groups"):
        val groups = List(assigned(), completed())
        val results = ConsolidationGroupCancellationPolicy(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.head.id)

      it("skips Completed groups"):
        val groups = List(completed(), created())
        val results = ConsolidationGroupCancellationPolicy(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.last.id)

      it("skips already Cancelled groups"):
        val groups = List(cancelled(), created())
        val results = ConsolidationGroupCancellationPolicy(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.last.id)

      it("cancels all four non-terminal states in one pass"):
        val groups =
          List(created(), picked(), readyForWorkstation(), assigned(), completed(), cancelled())
        val results = ConsolidationGroupCancellationPolicy(groups, at)
        assert(results.size == 4)

      it("emits one event per cancellation with correct fields"):
        val groups = List(created())
        val results = ConsolidationGroupCancellationPolicy(groups, at)
        val (cancelled, event) = results.head
        assert(event.consolidationGroupId == cancelled.id)
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("preserves orderIds in cancelled state for audit"):
        val groups = List(created())
        val (cancelled, _) = ConsolidationGroupCancellationPolicy(groups, at).head
        assert(cancelled.orderIds == orderIds)

    describe("when all groups are already terminal"):
      it("produces no cancellations"):
        val groups = List(completed(), cancelled())
        assert(ConsolidationGroupCancellationPolicy(groups, at).isEmpty)

    describe("when the list is empty"):
      it("produces no cancellations"):
        assert(ConsolidationGroupCancellationPolicy(List.empty, at).isEmpty)
