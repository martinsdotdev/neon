package neon.app

import neon.common.{GroupId, OrderId, WaveId, WorkstationId}
import neon.consolidationgroup.ConsolidationGroup
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class ConsolidationGroupCancellationPolicySuite extends AnyFunSpec:
  val waveId = WaveId()
  val orderIds = List(OrderId(), OrderId())
  val workstationId = WorkstationId()
  val at = Instant.now()

  def created() = ConsolidationGroup.Created(GroupId(), waveId, orderIds)

  def picked() = ConsolidationGroup.Picked(GroupId(), waveId, orderIds)

  def readyForWorkstation() = ConsolidationGroup.ReadyForWorkstation(GroupId(), waveId, orderIds)

  def assigned() = ConsolidationGroup.Assigned(GroupId(), waveId, orderIds, workstationId)

  def completed() = ConsolidationGroup.Completed(GroupId(), waveId, orderIds, workstationId)

  def cancelled() = ConsolidationGroup.Cancelled(GroupId(), waveId, orderIds)

  describe("ConsolidationGroupCancellationPolicy"):
    describe("when groups include non-terminal states"):
      it("cancels Created groups"):
        val groups = List(created(), completed())
        val results = ConsolidationGroupCancellationPolicy.evaluate(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.head.id)

      it("cancels Picked groups"):
        val groups = List(picked(), completed())
        val results = ConsolidationGroupCancellationPolicy.evaluate(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.head.id)

      it("cancels ReadyForWorkstation groups"):
        val groups = List(readyForWorkstation(), completed())
        val results = ConsolidationGroupCancellationPolicy.evaluate(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.head.id)

      it("cancels Assigned groups"):
        val groups = List(assigned(), completed())
        val results = ConsolidationGroupCancellationPolicy.evaluate(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.head.id)

      it("skips Completed groups"):
        val groups = List(completed(), created())
        val results = ConsolidationGroupCancellationPolicy.evaluate(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.last.id)

      it("skips already Cancelled groups"):
        val groups = List(cancelled(), created())
        val results = ConsolidationGroupCancellationPolicy.evaluate(groups, at)
        assert(results.size == 1)
        assert(results.head._1.id == groups.last.id)

      it("cancels mixed non-terminal states"):
        val groups =
          List(created(), picked(), readyForWorkstation(), assigned(), completed(), cancelled())
        val results = ConsolidationGroupCancellationPolicy.evaluate(groups, at)
        assert(results.size == 4)

      it("emits one event per cancellation with correct fields"):
        val groups = List(created())
        val results = ConsolidationGroupCancellationPolicy.evaluate(groups, at)
        val (cancelled, event) = results.head
        assert(event.groupId == cancelled.id)
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("preserves orderIds in cancelled state for audit"):
        val groups = List(created())
        val (cancelled, _) = ConsolidationGroupCancellationPolicy.evaluate(groups, at).head
        assert(cancelled.orderIds == orderIds)

    describe("when all groups are already terminal"):
      it("produces no cancellations"):
        val groups = List(completed(), cancelled())
        assert(ConsolidationGroupCancellationPolicy.evaluate(groups, at).isEmpty)

    describe("when the list is empty"):
      it("produces no cancellations"):
        assert(ConsolidationGroupCancellationPolicy.evaluate(List.empty, at).isEmpty)
