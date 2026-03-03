package neon.task

import neon.common.{
  HandlingUnitId,
  LocationId,
  OrderId,
  PackagingLevel,
  SkuId,
  TaskId,
  UserId,
  WaveId
}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TaskSuite extends AnyFunSpec:
  val taskId = TaskId()
  val skuId = SkuId()
  val userId = UserId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
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

  def allocated(
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ) =
    Task.Allocated(
      taskId,
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      waveId,
      None,
      handlingUnitId,
      sourceLocationId,
      destinationLocationId
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

      it("rejects zero requested quantity"):
        assertThrows[IllegalArgumentException]:
          Task.create(TaskType.Pick, skuId, PackagingLevel.Each, 0, orderId, None, None, None, at)

      it("rejects negative requested quantity"):
        assertThrows[IllegalArgumentException]:
          Task.create(TaskType.Pick, skuId, PackagingLevel.Each, -1, orderId, None, None, None, at)

    describe("allocating"):
      it("stores source and destination locations"):
        val (alloc, _) = planned().allocate(sourceLocationId, destinationLocationId, at)
        assert(alloc.sourceLocationId == sourceLocationId)
        assert(alloc.destinationLocationId == destinationLocationId)

      it("TaskAllocated event carries task identity, locations, and timestamp"):
        val (_, event) = planned().allocate(sourceLocationId, destinationLocationId, at)
        assert(event.taskId == taskId)
        assert(event.taskType == TaskType.Pick)
        assert(event.sourceLocationId == sourceLocationId)
        assert(event.destinationLocationId == destinationLocationId)
        assert(event.occurredAt == at)

      it("preserves all Planned fields"):
        val (alloc, _) = planned().allocate(sourceLocationId, destinationLocationId, at)
        assert(alloc.id == taskId)
        assert(alloc.taskType == TaskType.Pick)
        assert(alloc.skuId == skuId)
        assert(alloc.packagingLevel == PackagingLevel.Each)
        assert(alloc.requestedQty == 10)
        assert(alloc.orderId == orderId)
        assert(alloc.waveId == Some(waveId))
        assert(alloc.handlingUnitId == Some(handlingUnitId))

    describe("assigning"):
      it("binds the operator to the task"):
        val (assigned, _) = allocated().assign(userId, at)
        assert(assigned.assignedTo == userId)

      it("propagates locations from Allocated"):
        val (assigned, _) = allocated().assign(userId, at)
        assert(assigned.sourceLocationId == sourceLocationId)
        assert(assigned.destinationLocationId == destinationLocationId)

      it("TaskAssigned event carries operator ID and timestamp"):
        val (_, event) = allocated().assign(userId, at)
        assert(event.taskId == taskId)
        assert(event.userId == userId)
        assert(event.occurredAt == at)

    describe("completing"):
      it("records both requested and actual quantities"):
        val (assigned, _) = allocated().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.actualQty == 8)
        assert(completed.requestedQty == 10)

      it("TaskCompleted event carries all fields for downstream policies"):
        val (assigned, _) = allocated().assign(userId, at)
        val (_, event) = assigned.complete(8, at)
        assert(event.taskType == TaskType.Pick)
        assert(event.skuId == skuId)
        assert(event.packagingLevel == PackagingLevel.Each)
        assert(event.waveId == Some(waveId))
        assert(event.handlingUnitId == Some(handlingUnitId))
        assert(event.sourceLocationId == sourceLocationId)
        assert(event.destinationLocationId == destinationLocationId)
        assert(event.requestedQty == 10)
        assert(event.actualQty == 8)
        assert(event.assignedTo == userId)
        assert(event.occurredAt == at)

      it("preserves operator, packaging level, and locations"):
        val (assigned, _) = allocated().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.assignedTo == userId)
        assert(completed.packagingLevel == PackagingLevel.Each)
        assert(completed.sourceLocationId == sourceLocationId)
        assert(completed.destinationLocationId == destinationLocationId)

      it("allows zero actual quantity for full shortpick"):
        val (assigned, _) = allocated().assign(userId, at)
        val (completed, _) = assigned.complete(0, at)
        assert(completed.actualQty == 0)

      it("allows actual quantity greater than requested for over-pick"):
        val (assigned, _) = allocated().assign(userId, at)
        val (completed, _) = assigned.complete(12, at)
        assert(completed.actualQty == 12)
        assert(completed.requestedQty == 10)

      it("rejects negative actual quantity"):
        val (assigned, _) = allocated().assign(userId, at)
        assertThrows[IllegalArgumentException]:
          assigned.complete(-1, at)

    describe("cancelling"):
      describe("from Planned"):
        it("omits locations and assignedTo"):
          val (cancelled, event) = planned().cancel(at)
          assert(cancelled.sourceLocationId == None)
          assert(cancelled.destinationLocationId == None)
          assert(cancelled.assignedTo == None)
          assert(event.sourceLocationId == None)
          assert(event.destinationLocationId == None)
          assert(event.assignedTo == None)

      describe("from Allocated"):
        it("preserves locations but omits assignedTo"):
          val (cancelled, event) = allocated().cancel(at)
          assert(cancelled.sourceLocationId == Some(sourceLocationId))
          assert(cancelled.destinationLocationId == Some(destinationLocationId))
          assert(cancelled.assignedTo == None)
          assert(event.sourceLocationId == Some(sourceLocationId))
          assert(event.destinationLocationId == Some(destinationLocationId))
          assert(event.assignedTo == None)

      describe("from Assigned"):
        it("preserves locations and operator ID"):
          val (assigned, _) = allocated().assign(userId, at)
          val (cancelled, event) = assigned.cancel(at)
          assert(cancelled.sourceLocationId == Some(sourceLocationId))
          assert(cancelled.destinationLocationId == Some(destinationLocationId))
          assert(cancelled.assignedTo == Some(userId))
          assert(event.sourceLocationId == Some(sourceLocationId))
          assert(event.destinationLocationId == Some(destinationLocationId))
          assert(event.assignedTo == Some(userId))

      it("TaskCancelled event carries wave ID"):
        val (_, event) = planned().cancel(at)
        assert(event.waveId == Some(waveId))
        val (_, eventNoWave) = planned(waveId = None).cancel(at)
        assert(eventNoWave.waveId == None)

      it("TaskCancelled event carries handling unit ID"):
        val (_, event) = planned().cancel(at)
        assert(event.handlingUnitId == Some(handlingUnitId))

      it("preserves packaging level"):
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

      def withParentAllocated() =
        Task.Allocated(
          taskId,
          TaskType.Pick,
          skuId,
          PackagingLevel.Each,
          10,
          orderId,
          Some(waveId),
          Some(parentId),
          Some(handlingUnitId),
          sourceLocationId,
          destinationLocationId
        )

      it("Completed state preserves parent task reference"):
        val (assigned, _) = withParentAllocated().assign(userId, at)
        val (completed, _) = assigned.complete(8, at)
        assert(completed.parentTaskId == Some(parentId))

      it("TaskCompleted event includes parent task reference"):
        val (assigned, _) = withParentAllocated().assign(userId, at)
        val (_, event) = assigned.complete(8, at)
        assert(event.parentTaskId == Some(parentId))

      it("Cancelled state preserves parent task reference"):
        val (cancelled, _) = withParent().cancel(at)
        assert(cancelled.parentTaskId == Some(parentId))

      it("TaskCancelled event includes parent task reference"):
        val (_, event) = withParent().cancel(at)
        assert(event.parentTaskId == Some(parentId))

      it("omits parent task reference for non-replacement tasks"):
        val (assigned, _) = allocated().assign(userId, at)
        val (completed, event) = assigned.complete(10, at)
        assert(completed.parentTaskId == None)
        assert(event.parentTaskId == None)

    describe("full lifecycle"):
      it("preserves identity across Planned → Allocated → Assigned → Completed"):
        val (p, created) = Task.create(
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
        val (a, allocated) = p.allocate(sourceLocationId, destinationLocationId, at)
        val (s, assigned) = a.assign(userId, at)
        val (c, completed) = s.complete(10, at)
        assert(c.id == p.id)
        assert(c.taskType == TaskType.Pick)
        assert(c.skuId == skuId)
        assert(c.orderId == orderId)
        assert(c.sourceLocationId == sourceLocationId)
        assert(c.destinationLocationId == destinationLocationId)
        assert(c.assignedTo == userId)
        assert(created.taskId == p.id)
        assert(allocated.taskId == p.id)
        assert(assigned.taskId == p.id)
        assert(completed.taskId == p.id)
