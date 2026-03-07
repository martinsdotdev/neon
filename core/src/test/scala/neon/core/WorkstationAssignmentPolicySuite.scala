package neon.core

import neon.common.{ConsolidationGroupId, OrderId, WaveId, WorkstationId}
import neon.consolidationgroup.ConsolidationGroup
import neon.workstation.{Workstation, WorkstationType}
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class WorkstationAssignmentPolicySuite extends AnyFunSpec with OptionValues:
  val waveId = WaveId()
  val orderIds = List(OrderId(), OrderId())
  val at = Instant.now()

  def readyConsolidationGroup() =
    ConsolidationGroup.ReadyForWorkstation(ConsolidationGroupId(), waveId, orderIds)

  def idleWorkstation(slots: Int = 8) =
    Workstation.Idle(WorkstationId(), WorkstationType.PutWall, slots)

  describe("WorkstationAssignmentPolicy"):
    describe("capacity check"):
      it("rejects assignment when workstation has fewer slots than orders"):
        val consolidationGroup = readyConsolidationGroup()
        val workstation = idleWorkstation(slots = 1)
        val result = WorkstationAssignmentPolicy(consolidationGroup, workstation, at)
        assert(result.isEmpty)

      it("accepts assignment when workstation has exactly enough slots"):
        val consolidationGroup = readyConsolidationGroup()
        val workstation = idleWorkstation(slots = 2)
        val result = WorkstationAssignmentPolicy(consolidationGroup, workstation, at)
        assert(result.isDefined)

    describe("consolidation group transition"):
      it("assigns the consolidation group to the workstation"):
        val consolidationGroup = readyConsolidationGroup()
        val workstation = idleWorkstation()
        val ((assigned, _), _) =
          WorkstationAssignmentPolicy(consolidationGroup, workstation, at).value
        assert(assigned.id == consolidationGroup.id)
        assert(assigned.workstationId == workstation.id)

      it("consolidation group event carries workstationId and occurredAt"):
        val consolidationGroup = readyConsolidationGroup()
        val workstation = idleWorkstation()
        val ((_, consolidationGroupEvent), _) =
          WorkstationAssignmentPolicy(consolidationGroup, workstation, at).value
        assert(consolidationGroupEvent.workstationId == workstation.id)
        assert(consolidationGroupEvent.occurredAt == at)

    describe("workstation transition"):
      it("activates the workstation with the consolidation group"):
        val consolidationGroup = readyConsolidationGroup()
        val workstation = idleWorkstation()
        val (_, (active, _)) =
          WorkstationAssignmentPolicy(consolidationGroup, workstation, at).value
        assert(active.id == workstation.id)
        assert(active.consolidationGroupId == consolidationGroup.id)

      it("workstation event carries consolidationGroupId and occurredAt"):
        val consolidationGroup = readyConsolidationGroup()
        val workstation = idleWorkstation()
        val (_, (_, workstationEvent)) =
          WorkstationAssignmentPolicy(consolidationGroup, workstation, at).value
        assert(workstationEvent.consolidationGroupId == consolidationGroup.id)
        assert(workstationEvent.occurredAt == at)

      it("preserves workstation type after assignment"):
        val workstation = Workstation.Idle(WorkstationId(), WorkstationType.PackStation, 8)
        val (_, (active, event)) =
          WorkstationAssignmentPolicy(readyConsolidationGroup(), workstation, at).value
        assert(active.workstationType == WorkstationType.PackStation)
        assert(event.workstationType == WorkstationType.PackStation)

    describe("cross-aggregate consistency"):
      it("consolidation group's workstationId matches workstation's id"):
        val consolidationGroup = readyConsolidationGroup()
        val workstation = idleWorkstation()
        val ((assigned, _), (active, _)) =
          WorkstationAssignmentPolicy(consolidationGroup, workstation, at).value
        assert(assigned.workstationId == active.id)
        assert(active.consolidationGroupId == assigned.id)
