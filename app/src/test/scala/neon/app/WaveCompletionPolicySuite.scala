package neon.app

import neon.common.{OrderId, SkuId, TaskId, WaveId}
import neon.task.{Task, TaskType}
import neon.wave.{OrderGrouping, Wave}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.OptionValues

class WaveCompletionPolicySuite extends AnyFunSpec with OptionValues:
  val waveId = WaveId()
  val skuId = SkuId()
  val orderIds = List(OrderId(), OrderId())

  def released() = Wave.Released(waveId, OrderGrouping.Multi, orderIds)

  def completedTask() =
    Task.Completed(TaskId(), TaskType.Pick, skuId, 10, 10, Some(waveId))

  def cancelledTask() =
    Task.Cancelled(TaskId(), TaskType.Pick, skuId, Some(waveId))

  def assignedTask() =
    Task.Assigned(TaskId(), TaskType.Pick, skuId, 10, Some(waveId), None, neon.common.UserId())

  describe("WaveCompletionPolicy"):
    describe("when all tasks are terminal"):
      it("completes the wave"):
        val tasks = List(completedTask(), completedTask(), completedTask())
        val (_, event) = WaveCompletionPolicy.evaluate(tasks, released()).value
        assert(event.waveId == waveId)

      it("treats cancelled tasks as terminal"):
        val tasks = List(completedTask(), cancelledTask(), completedTask())
        assert(WaveCompletionPolicy.evaluate(tasks, released()).isDefined)

    describe("when tasks are still open"):
      it("does not complete the wave"):
        val tasks = List(completedTask(), assignedTask(), completedTask())
        assert(WaveCompletionPolicy.evaluate(tasks, released()).isEmpty)

    describe("when the task list is empty"):
      it("does not complete the wave"):
        assert(WaveCompletionPolicy.evaluate(List.empty, released()).isEmpty)
