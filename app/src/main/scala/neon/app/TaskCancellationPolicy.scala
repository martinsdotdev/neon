package neon.app

import neon.task.{Task, TaskEvent}
import java.time.Instant

object TaskCancellationPolicy:
  def evaluate(
      waveTasks: List[Task],
      at: Instant
  ): List[(Task.Cancelled, TaskEvent.TaskCancelled)] =
    waveTasks.collect:
      case t: Task.Planned  => t.cancel(at)
      case t: Task.Assigned => t.cancel(at)
