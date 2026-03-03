package neon.workstation

import neon.common.{GroupId, WorkstationId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class WorkstationSuite extends AnyFunSpec:
  val id = WorkstationId()
  val groupId = GroupId()
  val at = Instant.now()

  def disabled() = Workstation.Disabled(id, WorkstationType.PutWall)
  def idle() = Workstation.Idle(id, WorkstationType.PutWall)

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
      it("binds a consolidation group to this workstation"):
        val (active, _) = idle().assign(groupId, at)
        assert(active.groupId == groupId)

      it("emits an event linking workstation to group"):
        val (_, event) = idle().assign(groupId, at)
        assert(event.workstationId == id)
        assert(event.groupId == groupId)

    describe("releasing"):
      it("frees the workstation for the next group"):
        val (active, _) = idle().assign(groupId, at)
        val (released, _) = active.release(at)
        assert(released.isInstanceOf[Workstation.Idle])

      it("emits an event for scheduling"):
        val (active, _) = idle().assign(groupId, at)
        val (_, event) = active.release(at)
        assert(event.workstationId == id)

    describe("cycling"):
      it("processes multiple groups in sequence"):
        val firstGroup = GroupId()
        val secondGroup = GroupId()
        val (active1, _) = idle().assign(firstGroup, at)
        assert(active1.groupId == firstGroup)
        val (backToIdle, _) = active1.release(at)
        val (active2, _) = backToIdle.assign(secondGroup, at)
        assert(active2.groupId == secondGroup)

    describe("disabling"):
      it("takes an idle workstation offline"):
        val (d, _) = idle().disable(at)
        assert(d.isInstanceOf[Workstation.Disabled])

      it("force-stops an active workstation for maintenance"):
        val (active, _) = idle().assign(groupId, at)
        val (d, _) = active.disable(at)
        assert(d.isInstanceOf[Workstation.Disabled])

    describe("identity"):
      it("carries workstation type through all states"):
        val (idle, _) = disabled().enable(at)
        assert(idle.workstationType == WorkstationType.PutWall)
        val (active, _) = idle.assign(groupId, at)
        assert(active.workstationType == WorkstationType.PutWall)

      it("events carry workstation type for capacity routing"):
        val (_, enabledEvent) = disabled().enable(at)
        assert(enabledEvent.workstationType == WorkstationType.PutWall)
        val (active, assignedEvent) = idle().assign(groupId, at)
        assert(assignedEvent.workstationType == WorkstationType.PutWall)
        val (_, releasedEvent) = active.release(at)
        assert(releasedEvent.workstationType == WorkstationType.PutWall)
        val (_, disabledEvent) = idle().disable(at)
        assert(disabledEvent.workstationType == WorkstationType.PutWall)
