package neon.core

import neon.task.Task

/** Shared predicates for [[Task]] state evaluation, used by completion and cancellation policies
  * within the app package.
  */
private[core] object TaskPredicates:

  /** Returns `true` when the task is in [[Task.Completed]] or [[Task.Cancelled]] state.
    *
    * @param task
    *   the task to evaluate
    * @return
    *   `true` if the task is terminal, `false` otherwise
    */
  def isTerminal(task: Task): Boolean = task match
    case _: Task.Completed => true
    case _: Task.Cancelled => true
    case _                 => false
