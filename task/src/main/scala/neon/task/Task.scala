package neon.task

import neon.common.{SkuId, TaskId, UserId, WaveId}
import java.time.Instant

sealed trait Task:
  def id: TaskId
  def taskType: TaskType
  def skuId: SkuId
  def waveId: Option[WaveId]

object Task:
  case class Planned(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      requestedQty: Int,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId]
  ) extends Task:
    def assign(userId: UserId): (Assigned, TaskEvent.TaskAssigned) =
      val assigned = Assigned(id, taskType, skuId, requestedQty, waveId, parentTaskId, userId)
      val event = TaskEvent.TaskAssigned(id, taskType, userId, Instant.now())
      (assigned, event)

    def cancel(): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled = Cancelled(id, taskType, skuId, waveId)
      val event = TaskEvent.TaskCancelled(id, taskType, waveId, Instant.now())
      (cancelled, event)

  case class Assigned(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      requestedQty: Int,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      assignedTo: UserId
  ) extends Task:
    def complete(actualQty: Int): (Completed, TaskEvent.TaskCompleted) =
      val completed = Completed(id, taskType, skuId, requestedQty, actualQty, waveId)
      val event =
        TaskEvent.TaskCompleted(id, taskType, skuId, waveId, requestedQty, actualQty, Instant.now())
      (completed, event)

    def cancel(): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled = Cancelled(id, taskType, skuId, waveId)
      val event = TaskEvent.TaskCancelled(id, taskType, waveId, Instant.now())
      (cancelled, event)

  case class Completed(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      requestedQty: Int,
      actualQty: Int,
      waveId: Option[WaveId]
  ) extends Task

  case class Cancelled(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      waveId: Option[WaveId]
  ) extends Task
