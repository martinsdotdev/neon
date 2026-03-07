package neon.core

import neon.task.Task
import neon.wave.{Wave, WaveEvent}

import java.time.Instant

/** Detects when all tasks in a wave have reached a terminal state and completes the wave.
  *
  * Returns [[None]] when the task list is empty or any task is still in a non-terminal state.
  */
object WaveCompletionPolicy:
  import TaskPredicates.isTerminal

  /** Completes the wave if every task is [[Task.Completed]] or [[Task.Cancelled]].
    *
    * @param waveTasks
    *   the current tasks belonging to the wave
    * @param wave
    *   the released wave to check for completion
    * @param at
    *   instant of the completion
    * @return
    *   completed wave and event if all tasks are terminal, [[None]] otherwise
    */
  def apply(
      waveTasks: List[Task],
      wave: Wave.Released,
      at: Instant
  ): Option[(Wave.Completed, WaveEvent.WaveCompleted)] =
    if waveTasks.isEmpty then None
    else if waveTasks.forall(isTerminal) then Some(wave.complete(at))
    else None
