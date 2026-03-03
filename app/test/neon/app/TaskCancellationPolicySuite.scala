package neon.app

import neon.common.{HandlingUnitId, OrderId, PackagingLevel, SkuId, TaskId, UserId, WaveId}
import neon.task.{Task, TaskType}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TaskCancellationPolicySuite extends AnyFunSpec:
  val skuId = SkuId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
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
      None
    )

  describe("TaskCancellationPolicy"):
    describe("when wave tasks include open tasks"):
      it("cancels planned tasks"):
        val tasks = List(plannedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("cancels assigned tasks"):
        val tasks = List(assignedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("skips already completed tasks"):
        val tasks = List(plannedTask(), completedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)

      it("skips already cancelled tasks"):
        val tasks = List(plannedTask(), cancelledTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("emits one event per cancelled task"):
        val tasks = List(plannedTask(), assignedTask(), completedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.size == 2)
        results.foreach: (cancelled, event) =>
          assert(event.occurredAt == at)
          assert(event.taskId == cancelled.id)
          assert(event.waveId == Some(waveId))
          assert(event.handlingUnitId == Some(handlingUnitId))

      it("planned cancellation carries no assignedTo"):
        val tasks = List(plannedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.head._1.assignedTo == None)
        assert(results.head._2.assignedTo == None)

      it("assigned cancellation carries the operator"):
        val tasks = List(assignedTask())
        val results = TaskCancellationPolicy(tasks, at)
        assert(results.head._1.assignedTo.isDefined)
        assert(results.head._2.assignedTo.isDefined)
        assert(results.head._1.assignedTo == results.head._2.assignedTo)

    describe("when all tasks are already terminal"):
      it("produces no cancellations"):
        val tasks = List(completedTask(), cancelledTask(), completedTask())
        assert(TaskCancellationPolicy(tasks, at).isEmpty)

    describe("when the task list is empty"):
      it("produces no cancellations"):
        assert(TaskCancellationPolicy(List.empty, at).isEmpty)
