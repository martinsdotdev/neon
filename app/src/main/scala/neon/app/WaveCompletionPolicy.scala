package neon.app

import neon.task.Task
import neon.wave.{Wave, WaveEvent}

object WaveCompletionPolicy:
  private def isTerminal(task: Task): Boolean = task match
    case _: Task.Completed => true
    case _: Task.Cancelled => true
    case _                 => false

  def evaluate(
      waveTasks: List[Task],
      wave: Wave.Released
  ): Option[(Wave.Completed, WaveEvent.WaveCompleted)] =
    if waveTasks.isEmpty then None
    else if waveTasks.forall(isTerminal) then Some(wave.complete())
    else None
