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

/** Domain events emitted by [[Task]] state transitions. */
sealed trait TaskEvent:

  /** The task that emitted this event. */
  def taskId: TaskId

  /** The type of warehouse operation for the emitting task. */
  def taskType: TaskType

  /** The instant at which the transition occurred. */
  def occurredAt: Instant

/** Event definitions for the [[Task]] aggregate. */
object TaskEvent:

  /** Emitted when a new task is created in the [[Task.Planned]] state. */
  case class TaskCreated(
      taskId: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      requestedQuantity: Int,
      occurredAt: Instant
  ) extends TaskEvent

  /** Emitted when a planned task is allocated to source and destination locations. */
  case class TaskAllocated(
      taskId: TaskId,
      taskType: TaskType,
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
      occurredAt: Instant
  ) extends TaskEvent

  /** Emitted when an allocated task is assigned to a user. */
  case class TaskAssigned(
      taskId: TaskId,
      taskType: TaskType,
      userId: UserId,
      occurredAt: Instant
  ) extends TaskEvent

  /** Emitted when an assigned task is completed with the actual quantity handled. */
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
      requestedQuantity: Int,
      actualQuantity: Int,
      assignedTo: UserId,
      occurredAt: Instant
  ) extends TaskEvent

  /** Emitted when a task is cancelled from any non-terminal state. */
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
