package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.workstation.{Workstation, WorkstationEvent}

import java.time.Instant

object WorkstationAssignmentPolicy:
  def apply(
      group: ConsolidationGroup.ReadyForWorkstation,
      workstation: Workstation.Idle,
      at: Instant
  ): (
      (ConsolidationGroup.Assigned, ConsolidationGroupEvent.ConsolidationGroupAssigned),
      (Workstation.Active, WorkstationEvent.WorkstationAssigned)
  ) =
    val cgResult = group.assign(workstation.id, at)
    val wsResult = workstation.assign(group.id, at)
    (cgResult, wsResult)
