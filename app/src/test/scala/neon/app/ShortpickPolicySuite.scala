package neon.app

import neon.common.{SkuId, TaskId, WaveId}
import neon.task.{Task, TaskType}

class ShortpickPolicySuite extends munit.FunSuite:
  val skuId = SkuId()
  val waveId = WaveId()

  def completed(requested: Int, actual: Int, waveId: Option[WaveId] = Some(waveId)) =
    Task.Completed(TaskId(), TaskType.Pick, skuId, requested, actual, waveId)

  test("no shortpick when actual meets requested"):
    val result = ShortpickPolicy.evaluate(completed(10, 10))
    assertEquals(result, None)

  test("shortpick creates task for remaining quantity"):
    val result = ShortpickPolicy.evaluate(completed(10, 7))
    assert(result.isDefined)
    assertEquals(result.get.requestedQty, 3)

  test("replacement task inherits wave and sku"):
    val task = completed(10, 4)
    val replacement = ShortpickPolicy.evaluate(task).get
    assertEquals(replacement.waveId, task.waveId)
    assertEquals(replacement.skuId, task.skuId)
    assertEquals(replacement.taskType, task.taskType)

  test("replacement task links to original via parent"):
    val task = completed(10, 6)
    val replacement = ShortpickPolicy.evaluate(task).get
    assertEquals(replacement.parentTaskId, Some(task.id))
