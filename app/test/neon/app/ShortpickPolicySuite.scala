package neon.app

import neon.common.{HandlingUnitId, OrderId, PackagingLevel, SkuId, TaskId, UserId, WaveId}
import neon.task.{Task, TaskType}
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class ShortpickPolicySuite extends AnyFunSpec with OptionValues:
  val skuId = SkuId()
  val userId = UserId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val at = Instant.now()

  def completed(
      requested: Int,
      actual: Int,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId),
      packagingLevel: PackagingLevel = PackagingLevel.Each
  ) =
    Task.Completed(
      TaskId(),
      TaskType.Pick,
      skuId,
      packagingLevel,
      requested,
      actual,
      orderId,
      waveId,
      None,
      handlingUnitId,
      userId
    )

  describe("ShortpickPolicy"):
    describe("when actual meets requested"):
      it("returns None"):
        assert(ShortpickPolicy(completed(10, 10), at).isEmpty)

    describe("when actual exceeds requested"):
      it("returns None"):
        assert(ShortpickPolicy(completed(10, 12), at).isEmpty)

    describe("when actual is zero"):
      it("returns replacement task with full requested quantity"):
        val (replacement, _) = ShortpickPolicy(completed(10, 0), at).value
        assert(replacement.requestedQty == 10)

    describe("when actual is less than requested"):
      val task = completed(10, 7)
      val (replacement, event) = ShortpickPolicy(task, at).value

      it("returns replacement task for the unfulfilled quantity"):
        assert(replacement.requestedQty == 3)

      it("copies wave, SKU, task type, and order ID from original"):
        assert(replacement.waveId == task.waveId)
        assert(replacement.skuId == task.skuId)
        assert(replacement.taskType == task.taskType)
        assert(replacement.orderId == task.orderId)

      it("copies packaging level from original"):
        assert(replacement.packagingLevel == task.packagingLevel)

      it("sets parentTaskId to the original task's ID"):
        assert(replacement.parentTaskId.value == task.id)

      it("preserves handling unit ID in replacement task"):
        assert(replacement.handlingUnitId == Some(handlingUnitId))

      it("TaskCreated event mirrors all replacement task fields"):
        assert(event.taskId == replacement.id)
        assert(event.taskType == task.taskType)
        assert(event.skuId == task.skuId)
        assert(event.packagingLevel == task.packagingLevel)
        assert(event.waveId == task.waveId)
        assert(event.handlingUnitId == Some(handlingUnitId))
        assert(event.requestedQty == 3)
        assert(event.parentTaskId.value == task.id)
        assert(event.occurredAt == at)

    describe("when task has no wave"):
      it("copies None waveId and handlingUnitId when original has no wave"):
        val task = completed(10, 7, waveId = None, handlingUnitId = None)
        val (replacement, _) = ShortpickPolicy(task, at).value
        assert(replacement.waveId == None)
        assert(replacement.handlingUnitId == None)

    describe("packagingLevel propagation"):
      it("copies Case packaging level to replacement task and event"):
        val task = completed(3, 1, packagingLevel = PackagingLevel.Case)
        val (replacement, event) = ShortpickPolicy(task, at).value
        assert(replacement.packagingLevel == PackagingLevel.Case)
        assert(event.packagingLevel == PackagingLevel.Case)
