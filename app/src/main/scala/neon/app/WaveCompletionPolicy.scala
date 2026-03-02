package neon.app

import neon.task.Task
import neon.wave.{Wave, WaveEvent}

import java.time.Instant

object WaveCompletionPolicy:
  private def isTerminal(task: Task): Boolean = task match
    case _: Task.Completed => true
    case _: Task.Cancelled => true
    case _                 => false

  def evaluate(
      waveTasks: List[Task],
      wave: Wave.Released,
      at: Instant
  ): Option[(Wave.Completed, WaveEvent.WaveCompleted)] =
    if waveTasks.isEmpty then None
    else if waveTasks.forall(isTerminal) then Some(wave.complete(at))
    else None
