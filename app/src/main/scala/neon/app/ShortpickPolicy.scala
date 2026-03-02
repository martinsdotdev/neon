package neon.app

import neon.task.{Task, TaskEvent}
import java.time.Instant

object ShortpickPolicy:
  def evaluate(
      completed: Task.Completed,
      at: Instant
  ): Option[(Task.Planned, TaskEvent.TaskCreated)] =
    val remainder = completed.requestedQty - completed.actualQty
    if remainder <= 0 then None
    else
      Some(
        Task.create(
          taskType = completed.taskType,
          skuId = completed.skuId,
          requestedQty = remainder,
          waveId = completed.waveId,
          parentTaskId = Some(completed.id),
          handlingUnitId = completed.handlingUnitId,
          at = at
        )
      )
