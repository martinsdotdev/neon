package neon.app

import neon.common.{OrderId, SkuId, TaskId, WaveId}
import neon.task.{Task, TaskType}
import neon.wave.{OrderGrouping, Wave}

class WaveCompletionPolicySuite extends munit.FunSuite:
  val waveId = WaveId()
  val skuId = SkuId()
  val orderIds = List(OrderId(), OrderId())

  def released() = Wave.Released(waveId, OrderGrouping.Multi, orderIds)

  def completedTask(waveId: WaveId = waveId) =
    Task.Completed(TaskId(), TaskType.Pick, skuId, 10, 10, Some(waveId))

  def cancelledTask(waveId: WaveId = waveId) =
    Task.Cancelled(TaskId(), TaskType.Pick, skuId, Some(waveId))

  def assignedTask(waveId: WaveId = waveId) =
    Task.Assigned(TaskId(), TaskType.Pick, skuId, 10, Some(waveId), None, neon.common.UserId())

  def plannedTask(waveId: WaveId = waveId) =
    Task.Planned(TaskId(), TaskType.Pick, skuId, 10, Some(waveId), None)

  test("wave completes when all its tasks are done"):
    val tasks = List(completedTask(), completedTask(), completedTask())
    val result = WaveCompletionPolicy.evaluate(tasks, released())
    assert(result.isDefined)
    val (completed, event) = result.get
    assertEquals(event.waveId, waveId)

  test("wave does not complete while tasks are still open"):
    val tasks = List(completedTask(), assignedTask(), completedTask())
    val result = WaveCompletionPolicy.evaluate(tasks, released())
    assertEquals(result, None)

  test("cancelled tasks count as done for wave completion"):
    val tasks = List(completedTask(), cancelledTask(), completedTask())
    val result = WaveCompletionPolicy.evaluate(tasks, released())
    assert(result.isDefined)

  test("wave does not complete with an empty task list"):
    val result = WaveCompletionPolicy.evaluate(List.empty, released())
    assertEquals(result, None)
