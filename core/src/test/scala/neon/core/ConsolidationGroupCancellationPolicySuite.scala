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
    describe("when consolidation groups include non-terminal states"):
      it("cancels Created consolidation groups"):
        val consolidationGroups = List(created(), completed())
        val results = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
        assert(results.size == 1)
        assert(results.head._1.id == consolidationGroups.head.id)

      it("cancels Picked consolidation groups"):
        val consolidationGroups = List(picked(), completed())
        val results = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
        assert(results.size == 1)
        assert(results.head._1.id == consolidationGroups.head.id)

      it("cancels ReadyForWorkstation consolidation groups"):
        val consolidationGroups = List(readyForWorkstation(), completed())
        val results = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
        assert(results.size == 1)
        assert(results.head._1.id == consolidationGroups.head.id)

      it("cancels Assigned consolidation groups"):
        val consolidationGroups = List(assigned(), completed())
        val results = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
        assert(results.size == 1)
        assert(results.head._1.id == consolidationGroups.head.id)

      it("skips Completed consolidation groups"):
        val consolidationGroups = List(completed(), created())
        val results = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
        assert(results.size == 1)
        assert(results.head._1.id == consolidationGroups.last.id)

      it("skips already Cancelled consolidation groups"):
        val consolidationGroups = List(cancelled(), created())
        val results = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
        assert(results.size == 1)
        assert(results.head._1.id == consolidationGroups.last.id)

      it("cancels all four non-terminal states in one pass"):
        val consolidationGroups =
          List(created(), picked(), readyForWorkstation(), assigned(), completed(), cancelled())
        val results = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
        assert(results.size == 4)

      it("emits one event per cancellation with correct fields"):
        val consolidationGroups = List(created())
        val results = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
        val (cancelled, event) = results.head
        assert(event.consolidationGroupId == cancelled.id)
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("preserves orderIds in cancelled state for audit"):
        val consolidationGroups = List(created())
        val (cancelled, _) = ConsolidationGroupCancellationPolicy(consolidationGroups, at).head
        assert(cancelled.orderIds == orderIds)

    describe("when all consolidation groups are already terminal"):
      it("produces no cancellations"):
        val consolidationGroups = List(completed(), cancelled())
        assert(ConsolidationGroupCancellationPolicy(consolidationGroups, at).isEmpty)

    describe("when the list is empty"):
      it("produces no cancellations"):
        assert(ConsolidationGroupCancellationPolicy(List.empty, at).isEmpty)
