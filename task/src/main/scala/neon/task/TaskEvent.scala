package neon.task

import neon.common.{HandlingUnitId, SkuId, TaskId, UserId, WaveId}
import java.time.Instant

sealed trait TaskEvent:
  def taskId: TaskId
  def taskType: TaskType
  def occurredAt: Instant

object TaskEvent:
  case class TaskCreated(
      taskId: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      requestedQty: Int,
      occurredAt: Instant
  ) extends TaskEvent

  case class TaskAssigned(
      taskId: TaskId,
      taskType: TaskType,
      userId: UserId,
      occurredAt: Instant
  ) extends TaskEvent

  case class TaskCompleted(
      taskId: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      requestedQty: Int,
      actualQty: Int,
      occurredAt: Instant
  ) extends TaskEvent

  case class TaskCancelled(
      taskId: TaskId,
      taskType: TaskType,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      occurredAt: Instant
  ) extends TaskEvent
