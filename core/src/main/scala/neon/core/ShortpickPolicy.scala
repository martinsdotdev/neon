package neon.core

import neon.task.{Task, TaskEvent}

import java.time.Instant

/** Creates a replacement [[Task.Planned]] for the unfulfilled remainder when a task completes with
  * less than the requested quantity (shortpick).
  *
  * Returns [[None]] when the full quantity was picked.
  */
object ShortpickPolicy:

  /** Creates a replacement task carrying the shortpicked remainder.
    *
    * The new task inherits all attributes from the completed task and sets
    * [[Task.Planned.parentTaskId]] to the completed task's id for traceability.
    *
    * @param completed
    *   the completed task to check for shortpick
    * @param at
    *   instant of the replacement task creation
    * @return
    *   replacement task and creation event, or [[None]] if fully picked
    */
  def apply(
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
          packagingLevel = completed.packagingLevel,
          requestedQty = remainder,
          orderId = completed.orderId,
          waveId = completed.waveId,
          parentTaskId = Some(completed.id),
          handlingUnitId = completed.handlingUnitId,
          at = at
        )
      )
