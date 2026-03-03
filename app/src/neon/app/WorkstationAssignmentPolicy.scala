package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.workstation.{Workstation, WorkstationEvent}

import java.time.Instant

object WorkstationAssignmentPolicy:
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
      val workstationResult = workstation.assign(consolidationGroup.id, at)
      Some((consolidationGroupResult, workstationResult))
