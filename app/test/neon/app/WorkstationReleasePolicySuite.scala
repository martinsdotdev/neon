package neon.app

import neon.common.{GroupId, WaveId, WorkstationId}
import neon.consolidationgroup.ConsolidationGroupEvent
import neon.workstation.{Workstation, WorkstationType}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class WorkstationReleasePolicySuite extends AnyFunSpec:
  val workstationId = WorkstationId()
  val groupId = GroupId()
  val waveId = WaveId()
  val at = Instant.now()

  def completedEvent() =
    ConsolidationGroupEvent.ConsolidationGroupCompleted(groupId, waveId, workstationId, at)

  def activeWorkstation(wsType: WorkstationType = WorkstationType.PutWall) =
    Workstation.Active(workstationId, wsType, groupId)

  describe("WorkstationReleasePolicy"):
    it("returns the workstation to idle"):
      val (idle, _) = WorkstationReleasePolicy(completedEvent(), activeWorkstation(), at)
      assert(idle.id == workstationId)

    it("emits a released event with workstationId and occurredAt"):
      val (_, event) = WorkstationReleasePolicy(completedEvent(), activeWorkstation(), at)
      assert(event.workstationId == workstationId)
      assert(event.occurredAt == at)

    it("preserves workstation type after release"):
      val ws = activeWorkstation(WorkstationType.PackStation)
      val (idle, event) = WorkstationReleasePolicy(completedEvent(), ws, at)
      assert(idle.workstationType == WorkstationType.PackStation)
      assert(event.workstationType == WorkstationType.PackStation)
