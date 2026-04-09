package neon.workstation

import neon.common.{ConsolidationGroupId, WorkstationMode, WorkstationId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import java.util.UUID

class WorkstationSuite extends AnyFunSpec:
  val id = WorkstationId()
  val consolidationGroupId = ConsolidationGroupId()
  val at = Instant.now()

  def disabled() = Workstation.Disabled(id, WorkstationType.PutWall, 8)
  def idle(mode: WorkstationMode = WorkstationMode.Picking) =
    Workstation.Idle(id, WorkstationType.PutWall, 8, mode)

  describe("Workstation"):
    describe("enabling"):
      it("makes the workstation available for assignment"):
        val (idle, _) = disabled().enable(at)
        assert(idle.isInstanceOf[Workstation.Idle])

      it("defaults to Picking mode"):
        val (idle, _) = disabled().enable(at)
        assert(idle.mode == WorkstationMode.Picking)

      it("emits an event for capacity tracking"):
        val (_, event) = disabled().enable(at)
        assert(event.workstationId == id)
        assert(event.occurredAt == at)

      it("enabled event carries mode"):
        val (_, event) = disabled().enable(at)
        assert(event.mode == WorkstationMode.Picking)

    describe("switching mode"):
      it("transitions to the new mode"):
        val (switched, _) = idle().switchMode(WorkstationMode.Receiving, at)
        assert(switched.mode == WorkstationMode.Receiving)

      it("emits a ModeSwitched event with previous and new mode"):
        val (_, event) =
          idle(WorkstationMode.Picking).switchMode(WorkstationMode.Counting, at)
        assert(event.workstationId == id)
        assert(event.previousMode == WorkstationMode.Picking)
        assert(event.newMode == WorkstationMode.Counting)
        assert(event.occurredAt == at)

      it("preserves workstation identity after switch"):
        val (switched, _) = idle().switchMode(WorkstationMode.Relocation, at)
        assert(switched.id == id)
        assert(switched.workstationType == WorkstationType.PutWall)
        assert(switched.slotCount == 8)

    describe("assigning"):
      it("binds the assignment to this workstation"):
        val assignmentId = consolidationGroupId.value
        val (active, _) = idle().assign(assignmentId, at)
        assert(active.assignmentId == assignmentId)

      it("emits an event linking workstation to assignment"):
        val assignmentId = consolidationGroupId.value
        val (_, event) = idle().assign(assignmentId, at)
        assert(event.workstationId == id)
        assert(event.assignmentId == assignmentId)
        assert(event.occurredAt == at)

      it("carries mode into the Active state"):
        val assignmentId = consolidationGroupId.value
        val (active, _) =
          idle(WorkstationMode.Receiving).assign(assignmentId, at)
        assert(active.mode == WorkstationMode.Receiving)

      it("event carries mode"):
        val assignmentId = consolidationGroupId.value
        val (_, event) =
          idle(WorkstationMode.Receiving).assign(assignmentId, at)
        assert(event.mode == WorkstationMode.Receiving)

    describe("releasing"):
      it("frees the workstation for the next assignment"):
        val assignmentId = consolidationGroupId.value
        val (active, _) = idle().assign(assignmentId, at)
        val (released, _) = active.release(at)
        assert(released.isInstanceOf[Workstation.Idle])

      it("preserves mode after release"):
        val assignmentId = consolidationGroupId.value
        val (active, _) =
          idle(WorkstationMode.Counting).assign(assignmentId, at)
        val (released, _) = active.release(at)
        assert(released.mode == WorkstationMode.Counting)

      it("emits an event for scheduling"):
        val assignmentId = consolidationGroupId.value
        val (active, _) = idle().assign(assignmentId, at)
        val (_, event) = active.release(at)
        assert(event.workstationId == id)
        assert(event.occurredAt == at)

    describe("cycling"):
      it("processes multiple assignments in sequence"):
        val firstAssignment = UUID.randomUUID()
        val secondAssignment = UUID.randomUUID()
        val (active1, _) = idle().assign(firstAssignment, at)
        assert(active1.assignmentId == firstAssignment)
        val (backToIdle, _) = active1.release(at)
        val (active2, _) = backToIdle.assign(secondAssignment, at)
        assert(active2.assignmentId == secondAssignment)

    describe("disabling"):
      it("takes an idle workstation offline"):
        val (d, _) = idle().disable(at)
        assert(d.isInstanceOf[Workstation.Disabled])

      it("force-stops an active workstation for maintenance"):
        val assignmentId = consolidationGroupId.value
        val (active, _) = idle().assign(assignmentId, at)
        val (d, _) = active.disable(at)
        assert(d.isInstanceOf[Workstation.Disabled])

    describe("identity"):
      it("carries workstation type through all states"):
        val (idle, _) = disabled().enable(at)
        assert(idle.workstationType == WorkstationType.PutWall)
        val (active, _) = idle.assign(consolidationGroupId.value, at)
        assert(active.workstationType == WorkstationType.PutWall)

      it("carries slot count through all states"):
        val (idle, _) = disabled().enable(at)
        assert(idle.slotCount == 8)
        val (active, _) = idle.assign(consolidationGroupId.value, at)
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
        val (active, assignedEvent) =
          idle().assign(consolidationGroupId.value, at)
        assert(assignedEvent.workstationType == WorkstationType.PutWall)
        val (_, releasedEvent) = active.release(at)
        assert(releasedEvent.workstationType == WorkstationType.PutWall)
        val (_, disabledEvent) = idle().disable(at)
        assert(disabledEvent.workstationType == WorkstationType.PutWall)
