package neon.app

import neon.common.{HandlingUnitId, SkuId, TaskId, UserId, WaveId}
import neon.task.{Task, TaskType}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TaskCancellationPolicySuite extends AnyFunSpec:
  val skuId = SkuId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val at = Instant.now()

  def plannedTask() =
    Task.Planned(TaskId(), TaskType.Pick, skuId, 10, Some(waveId), None, Some(handlingUnitId))

  def assignedTask() =
    Task.Assigned(
      TaskId(),
      TaskType.Pick,
      skuId,
      10,
      Some(waveId),
      None,
      Some(handlingUnitId),
      UserId()
    )

  def completedTask() =
    Task.Completed(TaskId(), TaskType.Pick, skuId, 10, 10, Some(waveId), None, Some(handlingUnitId))

  def cancelledTask() =
    Task.Cancelled(TaskId(), TaskType.Pick, skuId, Some(waveId), None, Some(handlingUnitId))

  describe("TaskCancellationPolicy"):
    describe("when wave tasks include open tasks"):
      it("cancels planned tasks"):
        val tasks = List(plannedTask(), completedTask())
        val results = TaskCancellationPolicy.evaluate(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("cancels assigned tasks"):
        val tasks = List(assignedTask(), completedTask())
        val results = TaskCancellationPolicy.evaluate(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("skips already completed tasks"):
        val tasks = List(plannedTask(), completedTask(), completedTask())
        val results = TaskCancellationPolicy.evaluate(tasks, at)
        assert(results.size == 1)

      it("skips already cancelled tasks"):
        val tasks = List(plannedTask(), cancelledTask())
        val results = TaskCancellationPolicy.evaluate(tasks, at)
        assert(results.size == 1)
        assert(results.head._1.id == tasks.head.id)

      it("returns events for each cancelled task"):
        val tasks = List(plannedTask(), assignedTask(), completedTask())
        val results = TaskCancellationPolicy.evaluate(tasks, at)
        assert(results.size == 2)
        results.foreach: (cancelled, event) =>
          assert(event.occurredAt == at)
          assert(event.taskId == cancelled.id)

    describe("when all tasks are already terminal"):
      it("returns an empty list"):
        val tasks = List(completedTask(), cancelledTask(), completedTask())
        assert(TaskCancellationPolicy.evaluate(tasks, at).isEmpty)

    describe("when the task list is empty"):
      it("returns an empty list"):
        assert(TaskCancellationPolicy.evaluate(List.empty, at).isEmpty)
