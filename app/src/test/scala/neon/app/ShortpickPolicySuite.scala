package neon.app

import neon.common.{HandlingUnitId, SkuId, TaskId, WaveId}
import neon.task.{Task, TaskType}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.OptionValues
import java.time.Instant

class ShortpickPolicySuite extends AnyFunSpec with OptionValues:
  val skuId = SkuId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val at = Instant.now()

  def completed(
      requested: Int,
      actual: Int,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ) =
    Task.Completed(TaskId(), TaskType.Pick, skuId, requested, actual, waveId, None, handlingUnitId)

  describe("ShortpickPolicy"):
    describe("when actual meets requested"):
      it("returns None"):
        assert(ShortpickPolicy.evaluate(completed(10, 10), at).isEmpty)

    describe("when actual exceeds requested"):
      it("returns None"):
        assert(ShortpickPolicy.evaluate(completed(10, 12), at).isEmpty)

    describe("when actual is zero"):
      it("creates a replacement for the entire quantity"):
        val (replacement, _) = ShortpickPolicy.evaluate(completed(10, 0), at).value
        assert(replacement.requestedQty == 10)

    describe("when actual is less than requested"):
      val task = completed(10, 7)
      val (replacement, event) = ShortpickPolicy.evaluate(task, at).value

      it("creates a task for the remaining quantity"):
        assert(replacement.requestedQty == 3)

      it("inherits wave, sku, and task type"):
        assert(replacement.waveId == task.waveId)
        assert(replacement.skuId == task.skuId)
        assert(replacement.taskType == task.taskType)

      it("links to the original via parentTaskId"):
        assert(replacement.parentTaskId.value == task.id)

      it("propagates handling unit ID to replacement task"):
        assert(replacement.handlingUnitId == Some(handlingUnitId))

      it("emits a TaskCreated event for the replacement"):
        assert(event.taskId == replacement.id)
        assert(event.requestedQty == 3)
        assert(event.parentTaskId.value == task.id)
        assert(event.occurredAt == at)
