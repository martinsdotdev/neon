package neon.core

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.workstation.{Workstation, WorkstationEvent}

import java.time.Instant

/** Assigns a [[ConsolidationGroup.ReadyForWorkstation]] to an idle [[Workstation.Idle]] if the
  * workstation has enough slots for all orders in the group.
  *
  * Returns [[None]] when the workstation's slot count is less than the number of orders in the
  * consolidation group.
  */
object WorkstationAssignmentPolicy:

  /** Assigns the consolidation group to the workstation, transitioning both aggregates atomically.
    *
    * @param consolidationGroup
    *   the group ready for workstation assignment
    * @param workstation
    *   the idle workstation candidate
    * @param at
    *   instant of the assignment
    * @return
    *   paired transitions for group and workstation, or [[None]] if insufficient slots
    */
  def apply(
      consolidationGroup: ConsolidationGroup.ReadyForWorkstation,
      workstation: Workstation.Idle,
      at: Instant
  ): Option[
    (
        (ConsolidationGroup.Assigned, ConsolidationGroupEvent.ConsolidationGroupAssigned),
        (Workstation.Active, WorkstationEvent.WorkstationAssigned)
    )
  ] =
    if workstation.slotCount < consolidationGroup.orderIds.size then None
    else
      val consolidationGroupResult = consolidationGroup.assign(workstation.id, at)
      val workstationResult = workstation.assign(consolidationGroup.id.value, at)
      Some((consolidationGroupResult, workstationResult))
