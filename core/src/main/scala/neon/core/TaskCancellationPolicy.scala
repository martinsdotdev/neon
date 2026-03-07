package neon.core

import neon.task.{Task, TaskEvent}

import java.time.Instant

/** Cancels all non-terminal tasks belonging to a cancelled wave.
  *
  * Tasks already in [[Task.Completed]] or [[Task.Cancelled]] are left unchanged.
  */
object TaskCancellationPolicy:

  /** Cancels each task that is still in [[Task.Planned]], [[Task.Allocated]], or [[Task.Assigned]]
    * state.
    *
    * @param waveTasks
    *   the tasks to evaluate for cancellation
    * @param at
    *   instant of the cancellation
    * @return
    *   cancelled tasks paired with their cancellation events
    */
  def apply(
      waveTasks: List[Task],
      at: Instant
  ): List[(Task.Cancelled, TaskEvent.TaskCancelled)] =
    waveTasks.collect:
      case t: Task.Planned   => t.cancel(at)
      case t: Task.Allocated => t.cancel(at)
      case t: Task.Assigned  => t.cancel(at)
