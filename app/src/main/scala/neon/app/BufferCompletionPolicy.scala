package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.handlingunit.HandlingUnit

import java.time.Instant

object BufferCompletionPolicy:
  private def isInBuffer(hu: HandlingUnit): Boolean = hu match
    case _: HandlingUnit.InBuffer => true
    case _                        => false

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
    else if handlingUnits.forall(isInBuffer) then Some(group.readyForWorkstation(at))
    else None
