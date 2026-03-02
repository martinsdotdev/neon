package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}

import java.time.Instant

object ConsolidationGroupCancellationPolicy:
  def evaluate(
      groups: List[ConsolidationGroup],
      at: Instant
  ): List[(ConsolidationGroup.Cancelled, ConsolidationGroupEvent.ConsolidationGroupCancelled)] =
    groups.collect:
      case g: ConsolidationGroup.Created             => g.cancel(at)
      case g: ConsolidationGroup.Picked              => g.cancel(at)
      case g: ConsolidationGroup.ReadyForWorkstation => g.cancel(at)
      case g: ConsolidationGroup.Assigned            => g.cancel(at)
