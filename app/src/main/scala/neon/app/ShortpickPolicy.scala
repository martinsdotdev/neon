package neon.app

import neon.common.TaskId
import neon.task.Task

object ShortpickPolicy:
  def evaluate(completed: Task.Completed): Option[Task.Planned] =
    val remainder = completed.requestedQty - completed.actualQty
    if remainder <= 0 then None
    else
      Some(
        Task.Planned(
          id = TaskId(),
          taskType = completed.taskType,
          skuId = completed.skuId,
          requestedQty = remainder,
          waveId = completed.waveId,
          parentTaskId = Some(completed.id)
        )
      )
