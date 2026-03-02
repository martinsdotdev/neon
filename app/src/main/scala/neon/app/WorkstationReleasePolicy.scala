package neon.app

import neon.consolidationgroup.ConsolidationGroupEvent
import neon.workstation.{Workstation, WorkstationEvent}

import java.time.Instant

object WorkstationReleasePolicy:
  def evaluate(
      event: ConsolidationGroupEvent.ConsolidationGroupCompleted,
      workstation: Workstation.Active,
      at: Instant
  ): (Workstation.Idle, WorkstationEvent.WorkstationReleased) =
    workstation.release(at)
