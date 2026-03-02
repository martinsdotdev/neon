package neon.task

import neon.common.{HandlingUnitId, SkuId, TaskId, UserId, WaveId}

import java.time.Instant

sealed trait Task:
  def id: TaskId
  def taskType: TaskType
  def skuId: SkuId
  def waveId: Option[WaveId]
  def handlingUnitId: Option[HandlingUnitId]

object Task:
  def create(
      taskType: TaskType,
      skuId: SkuId,
      requestedQty: Int,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      at: Instant
  ): (Planned, TaskEvent.TaskCreated) =
    require(requestedQty > 0, s"requestedQty must be positive, got $requestedQty")
    val id = TaskId()
    val planned = Planned(id, taskType, skuId, requestedQty, waveId, parentTaskId, handlingUnitId)
    val event =
      TaskEvent.TaskCreated(
        id,
        taskType,
        skuId,
        waveId,
        parentTaskId,
        handlingUnitId,
        requestedQty,
        at
      )
    (planned, event)

  case class Planned(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      requestedQty: Int,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId]
  ) extends Task:
    def assign(userId: UserId, at: Instant): (Assigned, TaskEvent.TaskAssigned) =
      val assigned =
        Assigned(id, taskType, skuId, requestedQty, waveId, parentTaskId, handlingUnitId, userId)
      val event = TaskEvent.TaskAssigned(id, taskType, userId, at)
      (assigned, event)

    def cancel(at: Instant): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled = Cancelled(id, taskType, skuId, waveId, parentTaskId, handlingUnitId)
      val event = TaskEvent.TaskCancelled(id, taskType, waveId, parentTaskId, handlingUnitId, at)
      (cancelled, event)

  case class Assigned(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      requestedQty: Int,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      assignedTo: UserId
  ) extends Task:
    def complete(actualQty: Int, at: Instant): (Completed, TaskEvent.TaskCompleted) =
      require(actualQty >= 0, s"actualQty must be non-negative, got $actualQty")
      val completed =
        Completed(
          id,
          taskType,
          skuId,
          requestedQty,
          actualQty,
          waveId,
          parentTaskId,
          handlingUnitId
        )
      val event =
        TaskEvent.TaskCompleted(
          id,
          taskType,
          skuId,
          waveId,
          parentTaskId,
          handlingUnitId,
          requestedQty,
          actualQty,
          at
        )
      (completed, event)

    def cancel(at: Instant): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled = Cancelled(id, taskType, skuId, waveId, parentTaskId, handlingUnitId)
      val event = TaskEvent.TaskCancelled(id, taskType, waveId, parentTaskId, handlingUnitId, at)
      (cancelled, event)

  case class Completed(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      requestedQty: Int,
      actualQty: Int,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId]
  ) extends Task

  case class Cancelled(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId]
  ) extends Task
