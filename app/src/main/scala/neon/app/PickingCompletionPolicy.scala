package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.task.Task

import java.time.Instant

object PickingCompletionPolicy:
  private def isTerminal(task: Task): Boolean = task match
    case _: Task.Completed => true
    case _: Task.Cancelled => true
    case _                 => false

  def evaluate(
      tasks: List[Task],
      group: ConsolidationGroup.Created,
      at: Instant
  ): Option[(ConsolidationGroup.Picked, ConsolidationGroupEvent.ConsolidationGroupPicked)] =
    if tasks.isEmpty then None
    else if tasks.forall(isTerminal) then Some(group.pick(at))
    else None
