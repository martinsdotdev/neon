package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.task.Task

import java.time.Instant

object PickingCompletionPolicy:
  import TaskPredicates.isTerminal

  def apply(
      tasks: List[Task],
      group: ConsolidationGroup.Created,
      at: Instant
  ): Option[(ConsolidationGroup.Picked, ConsolidationGroupEvent.ConsolidationGroupPicked)] =
    if tasks.isEmpty then None
    else if tasks.forall(isTerminal) then Some(group.pick(at))
    else None
