package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.handlingunit.HandlingUnit

import java.time.Instant

/** Detects when all pick handling units in a [[ConsolidationGroup.Picked]] have arrived at the
  * buffer and transitions the group to [[ConsolidationGroup.ReadyForWorkstation]].
  *
  * Returns [[None]] when the handling unit list is empty or any unit has not yet reached
  * [[HandlingUnit.InBuffer]].
  */
object BufferCompletionPolicy:
  private def isInBuffer(handlingUnit: HandlingUnit): Boolean = handlingUnit match
    case _: HandlingUnit.InBuffer => true
    case _                        => false

  /** Transitions the consolidation group to ReadyForWorkstation if every handling unit is
    * [[HandlingUnit.InBuffer]].
    *
    * @param handlingUnits
    *   the pick handling units for the group
    * @param consolidationGroup
    *   the group in Picked state to evaluate
    * @param at
    *   instant of the transition
    * @return
    *   ready-for-workstation group and event if all units are in buffer, [[None]] otherwise
    */
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
