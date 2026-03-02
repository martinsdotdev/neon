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
    it("releases the workstation to idle"):
      val (idle, _) = WorkstationReleasePolicy.evaluate(completedEvent(), activeWorkstation(), at)
      assert(idle.id == workstationId)
      assert(idle.isInstanceOf[Workstation.Idle])

    it("emits event with workstationId and occurredAt"):
      val (_, event) = WorkstationReleasePolicy.evaluate(completedEvent(), activeWorkstation(), at)
      assert(event.workstationId == workstationId)
      assert(event.occurredAt == at)

    it("preserves workstation type after release"):
      val ws = activeWorkstation(WorkstationType.PackStation)
      val (idle, event) = WorkstationReleasePolicy.evaluate(completedEvent(), ws, at)
      assert(idle.workstationType == WorkstationType.PackStation)
      assert(event.workstationType == WorkstationType.PackStation)
