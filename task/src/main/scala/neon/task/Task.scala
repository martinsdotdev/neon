package neon.task

import neon.common.{HandlingUnitId, OrderId, PackagingLevel, SkuId, TaskId, UserId, WaveId}

import java.time.Instant

sealed trait Task:
  def id: TaskId
  def taskType: TaskType
  def skuId: SkuId
  def orderId: OrderId
  def waveId: Option[WaveId]
  def handlingUnitId: Option[HandlingUnitId]

object Task:
  def create(
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      requestedQty: Int,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      at: Instant
  ): (Planned, TaskEvent.TaskCreated) =
    require(requestedQty > 0, s"requestedQty must be positive, got $requestedQty")
    val id = TaskId()
    val planned =
      Planned(
        id,
        taskType,
        skuId,
        packagingLevel,
        requestedQty,
        orderId,
        waveId,
        parentTaskId,
        handlingUnitId
      )
    val event =
      TaskEvent.TaskCreated(
        id,
        taskType,
        skuId,
        packagingLevel,
        orderId,
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
      packagingLevel: PackagingLevel,
      requestedQty: Int,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId]
  ) extends Task:
    def assign(userId: UserId, at: Instant): (Assigned, TaskEvent.TaskAssigned) =
      val assigned =
        Assigned(
          id,
          taskType,
          skuId,
          packagingLevel,
          requestedQty,
          orderId,
          waveId,
          parentTaskId,
          handlingUnitId,
          userId
        )
      val event = TaskEvent.TaskAssigned(id, taskType, userId, at)
      (assigned, event)

    def cancel(at: Instant): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled =
        Cancelled(
          id,
          taskType,
          skuId,
          packagingLevel,
          orderId,
          waveId,
          parentTaskId,
          handlingUnitId,
          None
        )
      val event =
        TaskEvent.TaskCancelled(id, taskType, waveId, parentTaskId, handlingUnitId, None, at)
      (cancelled, event)

  case class Assigned(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      requestedQty: Int,
      orderId: OrderId,
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
          packagingLevel,
          requestedQty,
          actualQty,
          orderId,
          waveId,
          parentTaskId,
          handlingUnitId,
          assignedTo
        )
      val event =
        TaskEvent.TaskCompleted(
          id,
          taskType,
          skuId,
          packagingLevel,
          waveId,
          parentTaskId,
          handlingUnitId,
          requestedQty,
          actualQty,
          assignedTo,
          at
        )
      (completed, event)

    def cancel(at: Instant): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled =
        Cancelled(
          id,
          taskType,
          skuId,
          packagingLevel,
          orderId,
          waveId,
          parentTaskId,
          handlingUnitId,
          Some(assignedTo)
        )
      val event = TaskEvent
        .TaskCancelled(id, taskType, waveId, parentTaskId, handlingUnitId, Some(assignedTo), at)
      (cancelled, event)

  case class Completed(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      requestedQty: Int,
      actualQty: Int,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      assignedTo: UserId
  ) extends Task

  case class Cancelled(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      assignedTo: Option[UserId]
  ) extends Task
