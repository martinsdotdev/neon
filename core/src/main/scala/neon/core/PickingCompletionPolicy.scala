package neon.core

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.task.Task

import java.time.Instant

/** Detects when all tasks belonging to a [[ConsolidationGroup.Created]] have reached a terminal
  * state and transitions the group to [[ConsolidationGroup.Picked]].
  *
  * Returns [[None]] when the task list is empty or any task is still in a non-terminal state.
  */
object PickingCompletionPolicy:
  import TaskPredicates.isTerminal

  /** Transitions the consolidation group to Picked if every task is [[Task.Completed]] or
    * [[Task.Cancelled]].
    *
    * @param tasks
    *   the current tasks for the consolidation group
    * @param consolidationGroup
    *   the group in Created state to evaluate
    * @param at
    *   instant of the transition
    * @return
    *   picked group and event if all tasks are terminal, [[None]] otherwise
    */
  def apply(
      tasks: List[Task],
      consolidationGroup: ConsolidationGroup.Created,
      at: Instant
  ): Option[(ConsolidationGroup.Picked, ConsolidationGroupEvent.ConsolidationGroupPicked)] =
    if tasks.isEmpty then None
    else if tasks.forall(isTerminal) then Some(consolidationGroup.pick(at))
    else None
