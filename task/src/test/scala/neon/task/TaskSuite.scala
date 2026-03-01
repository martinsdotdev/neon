package neon.task

import neon.common.{SkuId, TaskId, UserId, WaveId}
import org.scalatest.funspec.AnyFunSpec

class TaskSuite extends AnyFunSpec:
  val taskId = TaskId()
  val skuId = SkuId()
  val userId = UserId()
  val waveId = WaveId()

  def planned(waveId: Option[WaveId] = Some(waveId)) =
    Task.Planned(taskId, TaskType.Pick, skuId, 10, waveId, None)

  describe("Task"):
    describe("assigning"):
      it("designates who performs the work"):
        val (assigned, _) = planned().assign(userId)
        assert(assigned.assignedTo == userId)

      it("emits an event identifying the operator"):
        val (_, event) = planned().assign(userId)
        assert(event.taskId == taskId)
        assert(event.userId == userId)

    describe("completing"):
      it("records the actual quantity"):
        val (assigned, _) = planned().assign(userId)
        val (completed, _) = assigned.complete(8)
        assert(completed.actualQty == 8)
        assert(completed.requestedQty == 10)

      it("carries task type for downstream routing"):
        val (assigned, _) = planned().assign(userId)
        val (_, event) = assigned.complete(10)
        assert(event.taskType == TaskType.Pick)
        assert(event.skuId == skuId)

    describe("cancelling"):
      it("can cancel a planned task before assignment"):
        val (_, event) = planned().cancel()
        assert(event.taskId == taskId)

      it("can cancel an assigned task to stop in-progress work"):
        val (assigned, _) = planned().assign(userId)
        val (_, event) = assigned.cancel()
        assert(event.taskId == taskId)

      it("carries wave ID for wave completion tracking"):
        val (_, event) = planned().cancel()
        assert(event.waveId == Some(waveId))
        val (_, eventNoWave) = planned(waveId = None).cancel()
        assert(eventNoWave.waveId == None)
