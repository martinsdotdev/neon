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
    it("assigns the consolidation group to the workstation"):
      val group = readyGroup()
      val ws = idleWorkstation()
      val ((assigned, _), _) = WorkstationAssignmentPolicy.evaluate(group, ws, at)
      assert(assigned.id == group.id)
      assert(assigned.workstationId == ws.id)

    it("activates the workstation with the group"):
      val group = readyGroup()
      val ws = idleWorkstation()
      val (_, (active, _)) = WorkstationAssignmentPolicy.evaluate(group, ws, at)
      assert(active.id == ws.id)
      assert(active.groupId == group.id)

    it("CG event carries workstationId and occurredAt"):
      val group = readyGroup()
      val ws = idleWorkstation()
      val ((_, cgEvent), _) = WorkstationAssignmentPolicy.evaluate(group, ws, at)
      assert(cgEvent.workstationId == ws.id)
      assert(cgEvent.occurredAt == at)

    it("workstation event carries groupId and occurredAt"):
      val group = readyGroup()
      val ws = idleWorkstation()
      val (_, (_, wsEvent)) = WorkstationAssignmentPolicy.evaluate(group, ws, at)
      assert(wsEvent.groupId == group.id)
      assert(wsEvent.occurredAt == at)

    it("preserves workstation type after assignment"):
      val ws = Workstation.Idle(WorkstationId(), WorkstationType.PackStation)
      val (_, (active, event)) = WorkstationAssignmentPolicy.evaluate(readyGroup(), ws, at)
      assert(active.workstationType == WorkstationType.PackStation)
      assert(event.workstationType == WorkstationType.PackStation)
