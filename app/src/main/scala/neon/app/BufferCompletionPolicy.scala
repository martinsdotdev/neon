package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.handlingunit.HandlingUnit

import java.time.Instant

object BufferCompletionPolicy:
  def evaluate(
      handlingUnits: List[HandlingUnit],
      group: ConsolidationGroup.Picked,
      at: Instant
  ): Option[
    (
        ConsolidationGroup.ReadyForWorkstation,
        ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation
    )
  ] =
    if handlingUnits.isEmpty then None
    else if handlingUnits.forall(_.isInstanceOf[HandlingUnit.InBuffer]) then
      Some(group.readyForWorkstation(at))
    else None
