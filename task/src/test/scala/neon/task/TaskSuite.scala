package neon.task

import neon.common.{SkuId, TaskId, UserId, WaveId}

class TaskSuite extends munit.FunSuite:
  val taskId = TaskId()
  val skuId = SkuId()
  val userId = UserId()
  val waveId = WaveId()

  def planned(waveId: Option[WaveId] = Some(waveId)) =
    Task.Planned(taskId, TaskType.Pick, skuId, 10, waveId, None)

  test("assigning a task designates who performs the work"):
    val (assigned, _) = planned().assign(userId)
    assertEquals(assigned.assignedTo, userId)

  test("the assignment event identifies the operator"):
    val (_, event) = planned().assign(userId)
    assertEquals(event.taskId, taskId)
    assertEquals(event.userId, userId)

  test("completing a task records the actual quantity"):
    val (assigned, _) = planned().assign(userId)
    val (completed, _) = assigned.complete(8)
    assertEquals(completed.actualQty, 8)
    assertEquals(completed.requestedQty, 10)

  test("the completion event carries task type for downstream routing"):
    val (assigned, _) = planned().assign(userId)
    val (_, event) = assigned.complete(10)
    assertEquals(event.taskType, TaskType.Pick)
    assertEquals(event.skuId, skuId)

  test("a planned task can be cancelled before assignment"):
    val (cancelled, event) = planned().cancel()
    assertEquals(event.taskId, taskId)

  test("an assigned task can be cancelled to stop in-progress work"):
    val (assigned, _) = planned().assign(userId)
    val (cancelled, event) = assigned.cancel()
    assertEquals(event.taskId, taskId)

  test("the cancellation event carries wave ID for wave tracking"):
    val (_, event) = planned().cancel()
    assertEquals(event.waveId, Some(waveId))
    val (_, eventNoWave) = planned(waveId = None).cancel()
    assertEquals(eventNoWave.waveId, None)
