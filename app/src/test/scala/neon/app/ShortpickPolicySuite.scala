package neon.app

import neon.common.{SkuId, TaskId, WaveId}
import neon.task.{Task, TaskType}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.OptionValues

class ShortpickPolicySuite extends AnyFunSpec with OptionValues:
  val skuId = SkuId()
  val waveId = WaveId()

  def completed(requested: Int, actual: Int, waveId: Option[WaveId] = Some(waveId)) =
    Task.Completed(TaskId(), TaskType.Pick, skuId, requested, actual, waveId)

  describe("ShortpickPolicy"):
    describe("when actual meets requested"):
      it("returns None"):
        assert(ShortpickPolicy.evaluate(completed(10, 10)).isEmpty)

    describe("when actual is less than requested"):
      val task = completed(10, 7)
      val replacement = ShortpickPolicy.evaluate(task).value

      it("creates a task for the remaining quantity"):
        assert(replacement.requestedQty == 3)

      it("inherits wave, sku, and task type"):
        assert(replacement.waveId == task.waveId)
        assert(replacement.skuId == task.skuId)
        assert(replacement.taskType == task.taskType)

      it("links to the original via parentTaskId"):
        assert(replacement.parentTaskId.value == task.id)
