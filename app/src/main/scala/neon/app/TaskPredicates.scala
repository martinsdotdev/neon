package neon.app

import neon.task.Task

private[app] object TaskPredicates:
  def isTerminal(task: Task): Boolean = task match
    case _: Task.Completed => true
    case _: Task.Cancelled => true
    case _                 => false
