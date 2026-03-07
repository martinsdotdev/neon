package neon.core

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}

import java.time.Instant

/** Cancels all non-terminal consolidation groups.
  *
  * Groups already in [[ConsolidationGroup.Completed]] or [[ConsolidationGroup.Cancelled]] are left
  * unchanged. Cancellation is reachable from any of the four non-terminal states: Created, Picked,
  * ReadyForWorkstation, and Assigned.
  */
object ConsolidationGroupCancellationPolicy:

  /** Cancels each consolidation group still in a non-terminal state.
    *
    * @param groups
    *   the consolidation groups to evaluate
    * @param at
    *   instant of the cancellation
    * @return
    *   cancelled groups paired with their cancellation events
    */
  def apply(
      groups: List[ConsolidationGroup],
      at: Instant
  ): List[(ConsolidationGroup.Cancelled, ConsolidationGroupEvent.ConsolidationGroupCancelled)] =
    groups.collect:
      case g: ConsolidationGroup.Created             => g.cancel(at)
      case g: ConsolidationGroup.Picked              => g.cancel(at)
      case g: ConsolidationGroup.ReadyForWorkstation => g.cancel(at)
      case g: ConsolidationGroup.Assigned            => g.cancel(at)
