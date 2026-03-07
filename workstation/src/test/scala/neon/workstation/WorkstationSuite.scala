package neon.workstation

import neon.common.{ConsolidationGroupId, WorkstationId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class WorkstationSuite extends AnyFunSpec:
  val id = WorkstationId()
  val consolidationGroupId = ConsolidationGroupId()
  val at = Instant.now()

  def disabled() = Workstation.Disabled(id, WorkstationType.PutWall, 8)
  def idle() = Workstation.Idle(id, WorkstationType.PutWall, 8)

  describe("Workstation"):
    describe("enabling"):
      it("makes the workstation available for assignment"):
        val (idle, _) = disabled().enable(at)
        assert(idle.isInstanceOf[Workstation.Idle])

      it("emits an event for capacity tracking"):
        val (_, event) = disabled().enable(at)
        assert(event.workstationId == id)
        assert(event.occurredAt == at)

    describe("assigning"):
      it("binds the consolidation group to this workstation"):
        val (active, _) = idle().assign(consolidationGroupId, at)
        assert(active.consolidationGroupId == consolidationGroupId)

      it("emits an event linking workstation to consolidation group"):
        val (_, event) = idle().assign(consolidationGroupId, at)
        assert(event.workstationId == id)
        assert(event.consolidationGroupId == consolidationGroupId)
        assert(event.occurredAt == at)

    describe("releasing"):
      it("frees the workstation for the next consolidation group"):
        val (active, _) = idle().assign(consolidationGroupId, at)
        val (released, _) = active.release(at)
        assert(released.isInstanceOf[Workstation.Idle])

      it("emits an event for scheduling"):
        val (active, _) = idle().assign(consolidationGroupId, at)
        val (_, event) = active.release(at)
        assert(event.workstationId == id)
        assert(event.occurredAt == at)

    describe("cycling"):
      it("processes multiple consolidation groups in sequence"):
        val firstConsolidationGroup = ConsolidationGroupId()
        val secondConsolidationGroup = ConsolidationGroupId()
        val (active1, _) = idle().assign(firstConsolidationGroup, at)
        assert(active1.consolidationGroupId == firstConsolidationGroup)
        val (backToIdle, _) = active1.release(at)
        val (active2, _) = backToIdle.assign(secondConsolidationGroup, at)
        assert(active2.consolidationGroupId == secondConsolidationGroup)

    describe("disabling"):
      it("takes an idle workstation offline"):
        val (d, _) = idle().disable(at)
        assert(d.isInstanceOf[Workstation.Disabled])

      it("force-stops an active workstation for maintenance"):
        val (active, _) = idle().assign(consolidationGroupId, at)
        val (d, _) = active.disable(at)
        assert(d.isInstanceOf[Workstation.Disabled])

    describe("identity"):
      it("carries workstation type through all states"):
        val (idle, _) = disabled().enable(at)
        assert(idle.workstationType == WorkstationType.PutWall)
        val (active, _) = idle.assign(consolidationGroupId, at)
        assert(active.workstationType == WorkstationType.PutWall)

      it("carries slot count through all states"):
        val (idle, _) = disabled().enable(at)
        assert(idle.slotCount == 8)
        val (active, _) = idle.assign(consolidationGroupId, at)
        assert(active.slotCount == 8)
        val (backToIdle, _) = active.release(at)
        assert(backToIdle.slotCount == 8)
        val (backToDisabled, _) = backToIdle.disable(at)
        assert(backToDisabled.slotCount == 8)

      it("enabled event carries slot count for capacity tracking"):
        val (_, event) = disabled().enable(at)
        assert(event.slotCount == 8)

      it("events carry workstation type for capacity routing"):
        val (_, enabledEvent) = disabled().enable(at)
        assert(enabledEvent.workstationType == WorkstationType.PutWall)
        val (active, assignedEvent) = idle().assign(consolidationGroupId, at)
        assert(assignedEvent.workstationType == WorkstationType.PutWall)
        val (_, releasedEvent) = active.release(at)
        assert(releasedEvent.workstationType == WorkstationType.PutWall)
        val (_, disabledEvent) = idle().disable(at)
        assert(disabledEvent.workstationType == WorkstationType.PutWall)
