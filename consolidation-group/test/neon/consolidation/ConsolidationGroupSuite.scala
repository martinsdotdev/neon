package neon.consolidationgroup

import neon.common.{GroupId, OrderId, WaveId, WorkstationId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class ConsolidationGroupSuite extends AnyFunSpec:
  val id = GroupId()
  val waveId = WaveId()
  val orderIds = List(OrderId(), OrderId())
  val workstationId = WorkstationId()
  val at = Instant.now()

  def created() = ConsolidationGroup.Created(id, waveId, orderIds)

  describe("ConsolidationGroup"):
    describe("creating"):
      it("produces a Created state and a ConsolidationGroupCreated event"):
        val (created, event) = ConsolidationGroup.create(waveId, orderIds, at)
        assert(created.id == event.groupId)
        assert(created.waveId == waveId)
        assert(created.orderIds == orderIds)

      it("event carries order IDs for downstream slot allocation"):
        val (_, event) = ConsolidationGroup.create(waveId, orderIds, at)
        assert(event.orderIds == orderIds)
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("rejects empty order list"):
        assertThrows[IllegalArgumentException]:
          ConsolidationGroup.create(waveId, List.empty, at)

    describe("full lifecycle"):
      it("tracks orders from wave completion through put-wall processing"):
        val (picked, _) = created().pick(at)
        val (ready, _) = picked.readyForWorkstation(at)
        val (assigned, _) = ready.assign(workstationId, at)
        val (completed, _) = assigned.complete(at)
        assert(completed.isInstanceOf[ConsolidationGroup.Completed])

    describe("assigning"):
      it("binds the consolidation group to a put-wall for sorting"):
        val (picked, _) = created().pick(at)
        val (ready, _) = picked.readyForWorkstation(at)
        val (assigned, _) = ready.assign(workstationId, at)
        assert(assigned.workstationId == workstationId)

    describe("identity"):
      it("carries wave ID for completion tracking"):
        val (picked, _) = created().pick(at)
        assert(picked.waveId == waveId)
        val (ready, _) = picked.readyForWorkstation(at)
        assert(ready.waveId == waveId)
        val (assigned, _) = ready.assign(workstationId, at)
        assert(assigned.waveId == waveId)

      it("carries order IDs for slot allocation"):
        val (picked, _) = created().pick(at)
        assert(picked.orderIds == orderIds)
        val (assigned, _) =
          val (r, _) = picked.readyForWorkstation(at)
          r.assign(workstationId, at)
        assert(assigned.orderIds == orderIds)

    describe("events"):
      it("identify the consolidation group and wave for policy routing"):
        val (_, event) = created().pick(at)
        assert(event.groupId == id)
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("readyForWorkstation event carries consolidation group and wave for assignment policy"):
        val (picked, _) = created().pick(at)
        val (_, event) = picked.readyForWorkstation(at)
        assert(event.groupId == id)
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("assigned event carries workstation ID for workstation coordination"):
        val (picked, _) = created().pick(at)
        val (ready, _) = picked.readyForWorkstation(at)
        val (_, event) = ready.assign(workstationId, at)
        assert(event.workstationId == workstationId)
        assert(event.occurredAt == at)

      it("completed event carries workstation ID to trigger release"):
        val (picked, _) = created().pick(at)
        val (ready, _) = picked.readyForWorkstation(at)
        val (assigned, _) = ready.assign(workstationId, at)
        val (_, event) = assigned.complete(at)
        assert(event.workstationId == workstationId)
        assert(event.occurredAt == at)

    describe("cancelling"):
      it("cancels from Created before any picking starts"):
        val (cancelled, _) = created().cancel(at)
        assert(cancelled.isInstanceOf[ConsolidationGroup.Cancelled])

      it("cancels from Picked when wave is aborted mid-flow"):
        val (picked, _) = created().pick(at)
        val (cancelled, _) = picked.cancel(at)
        assert(cancelled.isInstanceOf[ConsolidationGroup.Cancelled])

      it("cancels from ReadyForWorkstation when no workstation available"):
        val (picked, _) = created().pick(at)
        val (ready, _) = picked.readyForWorkstation(at)
        val (cancelled, _) = ready.cancel(at)
        assert(cancelled.isInstanceOf[ConsolidationGroup.Cancelled])

      it("cancels from Assigned when workstation must be freed"):
        val (picked, _) = created().pick(at)
        val (ready, _) = picked.readyForWorkstation(at)
        val (assigned, _) = ready.assign(workstationId, at)
        val (cancelled, _) = assigned.cancel(at)
        assert(cancelled.isInstanceOf[ConsolidationGroup.Cancelled])

      it("cancelled event carries consolidation group ID and wave ID for cascade coordination"):
        val (_, event) = created().cancel(at)
        assert(event.groupId == id)
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("cancelled state preserves order IDs for audit"):
        val (picked, _) = created().pick(at)
        val (cancelled, _) = picked.cancel(at)
        assert(cancelled.orderIds == orderIds)
