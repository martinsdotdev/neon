package neon.app

import neon.task.{Task, TaskEvent, TaskType}
import neon.wave.TaskRequest

import java.time.Instant

object TaskCreationPolicy:
  def apply(
      taskRequests: List[TaskRequest],
      at: Instant
  ): List[(Task.Planned, TaskEvent.TaskCreated)] =
    taskRequests.map: req =>
      Task.create(
        taskType = TaskType.Pick,
        skuId = req.skuId,
        packagingLevel = req.packagingLevel,
        requestedQty = req.quantity,
        waveId = Some(req.waveId),
        parentTaskId = None,
        handlingUnitId = None,
        at = at
      )
