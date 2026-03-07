package neon.core

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
import neon.task.{Task, TaskType}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TaskCancellationPolicySuite extends AnyFunSpec:
  val skuId = SkuId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val at = Instant.now()

  def plannedTask() =
    Task.Planned(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      Some(handlingUnitId)
    )

  def allocatedTask() =
    Task.Allocated(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      Some(handlingUnitId),
      sourceLocationId,
      destinationLocationId
    )

  def assignedTask() =
    Task.Assigned(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      Some(handlingUnitId),
      sourceLocationId,
      destinationLocationId,
      UserId()
    )

  def completedTask() =
    Task.Completed(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      10,
      orderId,
      Some(waveId),
      None,
      Some(handlingUnitId),
      sourceLocationId,
      destinationLocationId,
      UserId()
    )

  def cancelledTask() =
    Task.Cancelled(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      orderId,
      Some(waveId),
      None,
      Some(handlingUnitId),
      Some(sourceLocationId),
      Some(destinationLocationId),
      None
    )

  describe("TaskCancellationPolicy"):
    describe("when wave tasks include open tasks"):
      it("cancels Planned tasks"):
        val tasks = List(plannedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("cancels Allocated tasks"):
        val tasks = List(allocatedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("cancels Assigned tasks"):
        val tasks = List(assignedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("skips already Completed tasks"):
        val tasks = List(plannedTask(), completedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)

      it("skips already Cancelled tasks"):
        val tasks = List(plannedTask(), cancelledTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("emits one event per cancelled task"):
        val tasks = List(plannedTask(), allocatedTask(), assignedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 3)
        results.foreach: (cancelled, event) =>
          assert(event.occurredAt == at)
          assert(event.taskId == cancelled.id)
          assert(event.waveId == Some(waveId))
          assert(event.handlingUnitId == Some(handlingUnitId))

      it("omits assignedTo when cancelling from Planned"):
        val tasks = List(plannedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.head._1.assignedTo == None)
        assert(results.head._2.assignedTo == None)

      it("omits assignedTo when cancelling from Allocated"):
        val tasks = List(allocatedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.head._1.assignedTo == None)
        assert(results.head._2.assignedTo == None)

      it("preserves the operator when cancelling from Assigned"):
        val tasks = List(assignedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.head._1.assignedTo.isDefined)
        assert(results.head._2.assignedTo.isDefined)
        assert(results.head._1.assignedTo == results.head._2.assignedTo)

      it("preserves locations when cancelling from Allocated"):
        val tasks = List(allocatedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.head._1.sourceLocationId == Some(sourceLocationId))
        assert(results.head._1.destinationLocationId == Some(destinationLocationId))
        assert(results.head._2.sourceLocationId == Some(sourceLocationId))
        assert(results.head._2.destinationLocationId == Some(destinationLocationId))

      it("preserves locations when cancelling from Assigned"):
        val tasks = List(assignedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.head._1.sourceLocationId == Some(sourceLocationId))
        assert(results.head._1.destinationLocationId == Some(destinationLocationId))
        assert(results.head._2.sourceLocationId == Some(sourceLocationId))
        assert(results.head._2.destinationLocationId == Some(destinationLocationId))

      it("omits locations when cancelling from Planned"):
        val tasks = List(plannedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.head._1.sourceLocationId == None)
        assert(results.head._1.destinationLocationId == None)

    describe("when all tasks are already terminal"):
      it("produces no cancellations"):
        val tasks = List(completedTask(), cancelledTask(), completedTask())
        assert(TaskCancellationPolicy(tasks, at).isEmpty)

    describe("when the task list is empty"):
      it("produces no cancellations"):
        assert(TaskCancellationPolicy(List.empty, at).isEmpty)
