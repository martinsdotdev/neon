package neon.app

import neon.common.{GroupId, OrderId, WaveId, WorkstationId}
import neon.consolidationgroup.ConsolidationGroup
import neon.workstation.{Workstation, WorkstationType}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class WorkstationAssignmentPolicySuite extends AnyFunSpec:
  val waveId = WaveId()
  val orderIds = List(OrderId(), OrderId())
  val at = Instant.now()

  def readyGroup() =
    ConsolidationGroup.ReadyForWorkstation(GroupId(), waveId, orderIds)

  def idleWorkstation() =
    Workstation.Idle(WorkstationId(), WorkstationType.PutWall)

  describe("WorkstationAssignmentPolicy"):
    describe("consolidation group transition"):
      it("assigns the group to the workstation"):
        val group = readyGroup()
        val ws = idleWorkstation()
        val ((assigned, _), _) = WorkstationAssignmentPolicy(group, ws, at)
        assert(assigned.id == group.id)
        assert(assigned.workstationId == ws.id)

      it("group event carries workstationId and occurredAt"):
        val group = readyGroup()
        val ws = idleWorkstation()
        val ((_, cgEvent), _) = WorkstationAssignmentPolicy(group, ws, at)
        assert(cgEvent.workstationId == ws.id)
        assert(cgEvent.occurredAt == at)

    describe("workstation transition"):
      it("activates the workstation with the group"):
        val group = readyGroup()
        val ws = idleWorkstation()
        val (_, (active, _)) = WorkstationAssignmentPolicy(group, ws, at)
        assert(active.id == ws.id)
        assert(active.groupId == group.id)

      it("workstation event carries groupId and occurredAt"):
        val group = readyGroup()
        val ws = idleWorkstation()
        val (_, (_, wsEvent)) = WorkstationAssignmentPolicy(group, ws, at)
        assert(wsEvent.groupId == group.id)
        assert(wsEvent.occurredAt == at)

      it("preserves workstation type after assignment"):
        val ws = Workstation.Idle(WorkstationId(), WorkstationType.PackStation)
        val (_, (active, event)) = WorkstationAssignmentPolicy(readyGroup(), ws, at)
        assert(active.workstationType == WorkstationType.PackStation)
        assert(event.workstationType == WorkstationType.PackStation)

    describe("cross-aggregate consistency"):
      it("group's workstationId matches workstation's id"):
        val group = readyGroup()
        val ws = idleWorkstation()
        val ((assigned, _), (active, _)) = WorkstationAssignmentPolicy(group, ws, at)
        assert(assigned.workstationId == active.id)
        assert(active.groupId == assigned.id)
