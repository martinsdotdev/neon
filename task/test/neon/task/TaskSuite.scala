package neon.task

import neon.common.{HandlingUnitId, OrderId, PackagingLevel, SkuId, TaskId, UserId, WaveId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TaskSuite extends AnyFunSpec:
  val taskId = TaskId()
  val skuId = SkuId()
  val userId = UserId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val at = Instant.now()

  def planned(
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ) =
    Task.Planned(
      taskId,
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      waveId,
      None,
      handlingUnitId
    )

  describe("Task"):
    describe("creating"):
      it("returns Planned state and emits TaskCreated event"):
        val (planned, event) =
          Task.create(
            TaskType.Pick,
            skuId,
            PackagingLevel.Each,
            10,
            orderId,
            Some(waveId),
            None,
            Some(handlingUnitId),
            at
          )
        assert(planned.id == event.taskId)
        assert(planned.taskType == TaskType.Pick)
        assert(planned.requestedQty == 10)

      it("TaskCreated event includes all fields for event sourcing"):
        val parentId = TaskId()
        val (_, event) =
          Task.create(
            TaskType.Replenish,
            skuId,
            PackagingLevel.Case,
            5,
            orderId,
            None,
            Some(parentId),
            None,
            at
          )
        assert(event.taskType == TaskType.Replenish)
        assert(event.skuId == skuId)
        assert(event.packagingLevel == PackagingLevel.Case)
        assert(event.waveId == None)
        assert(event.parentTaskId == Some(parentId))
        assert(event.handlingUnitId == None)
        assert(event.requestedQty == 5)
        assert(event.occurredAt == at)

      it("throws IllegalArgumentException for zero requested quantity"):
        assertThrows[IllegalArgumentException]:
          Task.create(TaskType.Pick, skuId, PackagingLevel.Each, 0, orderId, None, None, None, at)

      it("throws IllegalArgumentException for negative requested quantity"):
        assertThrows[IllegalArgumentException]:
          Task.create(TaskType.Pick, skuId, PackagingLevel.Each, -1, orderId, None, None, None, at)

    describe("assigning"):
      it("stores operator ID in Assigned state"):
        val (assigned, _) = planned().assign(userId, at)
        assert(assigned.assignedTo == userId)

      it("TaskAssigned event includes operator ID"):
        val (_, event) = planned().assign(userId, at)
        assert(event.taskId == taskId)
        assert(event.userId == userId)

      it("TaskAssigned event includes assignment timestamp"):
        val (_, event) = planned().assign(userId, at)
        assert(event.occurredAt == at)

    describe("completing"):
      it("Completed state stores both actual and requested quantities"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.actualQty == 8)
        assert(completed.requestedQty == 10)

      it("TaskCompleted event includes task type and SKU ID"):
        val (assigned, _) = planned().assign(userId, at)
        val (_, event) = assigned.complete(10, at)
        assert(event.taskType == TaskType.Pick)
        assert(event.skuId == skuId)

      it("TaskCompleted event includes all fields for routing policies"):
        val (assigned, _) = planned().assign(userId, at)
        val (_, event) = assigned.complete(8, at)
        assert(event.packagingLevel == PackagingLevel.Each)
        assert(event.waveId == Some(waveId))
        assert(event.handlingUnitId == Some(handlingUnitId))
        assert(event.requestedQty == 10)
        assert(event.actualQty == 8)
        assert(event.assignedTo == userId)
        assert(event.occurredAt == at)

      it("Completed state preserves operator ID"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.assignedTo == userId)

      it("Completed state preserves packaging level"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.packagingLevel == PackagingLevel.Each)

      it("allows zero actual quantity (full shortpick scenario)"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, _) = assigned.complete(0, at)
        assert(completed.actualQty == 0)

      it("allows actual quantity greater than requested (over-picking)"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, _) = assigned.complete(12, at)
        assert(completed.actualQty == 12)
        assert(completed.requestedQty == 10)

      it("throws IllegalArgumentException for negative actual quantity"):
        val (assigned, _) = planned().assign(userId, at)
        assertThrows[IllegalArgumentException]:
          assigned.complete(-1, at)

    describe("cancelling"):
      it("Planned task transitions to Cancelled state"):
        val (_, event) = planned().cancel(at)
        assert(event.taskId == taskId)

      it("Assigned task transitions to Cancelled state"):
        val (assigned, _) = planned().assign(userId, at)
        val (_, event) = assigned.cancel(at)
        assert(event.taskId == taskId)

      it("TaskCancelled event includes wave ID"):
        val (_, event) = planned().cancel(at)
        assert(event.waveId == Some(waveId))
        val (_, eventNoWave) = planned(waveId = None).cancel(at)
        assert(eventNoWave.waveId == None)

      it("TaskCancelled event includes handling unit ID"):
        val (_, event) = planned().cancel(at)
        assert(event.handlingUnitId == Some(handlingUnitId))

      it("Planned task cancellation omits assignedTo"):
        val (cancelled, event) = planned().cancel(at)
        assert(cancelled.assignedTo == None)
        assert(event.assignedTo == None)

      it("Assigned task cancellation includes operator ID"):
        val (assigned, _) = planned().assign(userId, at)
        val (cancelled, event) = assigned.cancel(at)
        assert(cancelled.assignedTo == Some(userId))
        assert(event.assignedTo == Some(userId))

      it("Cancelled state preserves packaging level"):
        val (cancelled, _) = planned().cancel(at)
        assert(cancelled.packagingLevel == PackagingLevel.Each)

    describe("parentTaskId traceability"):
      val parentId = TaskId()

      def withParent() =
        Task.Planned(
          taskId,
          TaskType.Pick,
          skuId,
          PackagingLevel.Each,
          10,
          orderId,
          Some(waveId),
          Some(parentId),
          Some(handlingUnitId)
        )

      it("Completed state preserves parent task reference"):
        val (assigned, _) = withParent().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.parentTaskId == Some(parentId))

      it("TaskCompleted event includes parent task reference"):
        val (assigned, _) = withParent().assign(userId, at)
        val (_, event) = assigned.complete(8, at)
        assert(event.parentTaskId == Some(parentId))

      it("Cancelled state preserves parent task reference"):
        val (cancelled, _) = withParent().cancel(at)
        assert(cancelled.parentTaskId == Some(parentId))

      it("TaskCancelled event includes parent task reference"):
        val (_, event) = withParent().cancel(at)
        assert(event.parentTaskId == Some(parentId))

      it("omits parent task reference for non-replacement tasks"):
        val (assigned, _) = planned().assign(userId, at)
        val (completed, event) = assigned.complete(10, at)
        assert(completed.parentTaskId == None)
        assert(event.parentTaskId == None)
