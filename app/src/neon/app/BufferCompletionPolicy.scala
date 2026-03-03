package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.handlingunit.HandlingUnit

import java.time.Instant

object BufferCompletionPolicy:
  private def isInBuffer(handlingUnit: HandlingUnit): Boolean = handlingUnit match
    case _: HandlingUnit.InBuffer => true
    case _                        => false

  def apply(
      handlingUnits: List[HandlingUnit],
      consolidationGroup: ConsolidationGroup.Picked,
      at: Instant
  ): Option[
    (
        ConsolidationGroup.ReadyForWorkstation,
        ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation
    )
  ] =
    if handlingUnits.isEmpty then None
    else if handlingUnits.forall(isInBuffer) then Some(consolidationGroup.readyForWorkstation(at))
    else None
