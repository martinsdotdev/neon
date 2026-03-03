package neon.task

import neon.common.{
  HandlingUnitId,
  LocationId,
  OrderId,
  PackagingLevel,
  SkuId,
  TaskId,
  UserId,
  WaveId
}

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
      packagingLevel: PackagingLevel,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      requestedQty: Int,
      occurredAt: Instant
  ) extends TaskEvent

  case class TaskAllocated(
      taskId: TaskId,
      taskType: TaskType,
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
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
      packagingLevel: PackagingLevel,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
      requestedQty: Int,
      actualQty: Int,
      assignedTo: UserId,
      occurredAt: Instant
  ) extends TaskEvent

  case class TaskCancelled(
      taskId: TaskId,
      taskType: TaskType,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      sourceLocationId: Option[LocationId],
      destinationLocationId: Option[LocationId],
      assignedTo: Option[UserId],
      occurredAt: Instant
  ) extends TaskEvent
