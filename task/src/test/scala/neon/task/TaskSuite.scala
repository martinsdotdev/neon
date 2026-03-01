package neon.task

import neon.common.{SkuId, TaskId, UserId, WaveId}
import org.scalatest.funspec.AnyFunSpec
import java.time.Instant

class TaskSuite extends AnyFunSpec:
  val taskId = TaskId()
  val skuId = SkuId()
  val userId = UserId()
  val waveId = WaveId()
  val at = Instant.now()

  def planned(waveId: Option[WaveId] = Some(waveId)) =
    Task.Planned(taskId, TaskType.Pick, skuId, 10, waveId, None)

  describe("Task"):
    describe("assigning"):
      it("designates who performs the work"):
        val (assigned, _) = planned().assign(userId, at)
        assert(assigned.assignedTo == userId)

      it("emits an event identifying the operator"):
        val (_, event) = planned().assign(userId, at)
        assert(event.taskId == taskId)
        assert(event.userId == userId)

      it("stamps the event with the given instant"):
        val (_, event) = planned().assign(userId, at)
        assert(event.occurredAt == at)

    describe("completing"):
      it("records the actual quantity"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.actualQty == 8)
        assert(completed.requestedQty == 10)

      it("carries task type for downstream routing"):
        val (assigned, _) = planned().assign(userId, at)
        val (_, event) = assigned.complete(10, at)
        assert(event.taskType == TaskType.Pick)
        assert(event.skuId == skuId)

    describe("cancelling"):
      it("can cancel a planned task before assignment"):
        val (_, event) = planned().cancel(at)
        assert(event.taskId == taskId)

      it("can cancel an assigned task to stop in-progress work"):
        val (assigned, _) = planned().assign(userId, at)
        val (_, event) = assigned.cancel(at)
        assert(event.taskId == taskId)

      it("carries wave ID for wave completion tracking"):
        val (_, event) = planned().cancel(at)
        assert(event.waveId == Some(waveId))
        val (_, eventNoWave) = planned(waveId = None).cancel(at)
        assert(eventNoWave.waveId == None)
