package neon.task

import neon.common.{HandlingUnitId, SkuId, TaskId, UserId, WaveId}
import org.scalatest.funspec.AnyFunSpec
import java.time.Instant

class TaskSuite extends AnyFunSpec:
  val taskId = TaskId()
  val skuId = SkuId()
  val userId = UserId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val at = Instant.now()

  def planned(
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ) =
    Task.Planned(taskId, TaskType.Pick, skuId, 10, waveId, None, handlingUnitId)

  describe("Task"):
    describe("creating"):
      it("produces a Planned state and a TaskCreated event"):
        val (planned, event) = Task.create(TaskType.Pick, skuId, 10, Some(waveId), None, Some(handlingUnitId), at)
        assert(planned.id == event.taskId)
        assert(planned.taskType == TaskType.Pick)
        assert(planned.requestedQty == 10)

      it("event carries all fields for replay"):
        val parentId = TaskId()
        val (_, event) =
          Task.create(TaskType.Replenish, skuId, 5, None, Some(parentId), None, at)
        assert(event.taskType == TaskType.Replenish)
        assert(event.skuId == skuId)
        assert(event.waveId == None)
        assert(event.parentTaskId == Some(parentId))
        assert(event.handlingUnitId == None)
        assert(event.requestedQty == 5)
        assert(event.occurredAt == at)

      it("rejects zero requested quantity"):
        assertThrows[IllegalArgumentException]:
          Task.create(TaskType.Pick, skuId, 0, None, None, None, at)

      it("rejects negative requested quantity"):
        assertThrows[IllegalArgumentException]:
          Task.create(TaskType.Pick, skuId, -1, None, None, None, at)

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

      it("completed event carries handling unit ID for routing"):
        val (assigned, _) = planned().assign(userId, at)
        val (_, event) = assigned.complete(10, at)
        assert(event.handlingUnitId == Some(handlingUnitId))

      it("accepts zero actual quantity for full shortpick"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, _) = assigned.complete(0, at)
        assert(completed.actualQty == 0)

      it("rejects negative actual quantity"):
        val (assigned, _) = planned().assign(userId, at)
        assertThrows[IllegalArgumentException]:
          assigned.complete(-1, at)

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

      it("cancelled event carries handling unit ID"):
        val (_, event) = planned().cancel(at)
        assert(event.handlingUnitId == Some(handlingUnitId))

    describe("parentTaskId traceability"):
      val parentId = TaskId()

      def withParent() =
        Task.Planned(taskId, TaskType.Pick, skuId, 10, Some(waveId), Some(parentId), Some(handlingUnitId))

      it("completed state carries parentTaskId"):
        val (assigned, _) = withParent().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.parentTaskId == Some(parentId))

      it("completed event carries parentTaskId"):
        val (assigned, _) = withParent().assign(userId, at)
        val (_, event) = assigned.complete(8, at)
        assert(event.parentTaskId == Some(parentId))

      it("cancelled state carries parentTaskId"):
        val (cancelled, _) = withParent().cancel(at)
        assert(cancelled.parentTaskId == Some(parentId))

      it("cancelled event carries parentTaskId"):
        val (_, event) = withParent().cancel(at)
        assert(event.parentTaskId == Some(parentId))

      it("parentTaskId is None when not a replacement task"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, event) = assigned.complete(10, at)
        assert(completed.parentTaskId == None)
        assert(event.parentTaskId == None)
