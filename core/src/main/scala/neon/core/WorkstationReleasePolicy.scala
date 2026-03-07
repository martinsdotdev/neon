package neon.core

import neon.consolidationgroup.ConsolidationGroupEvent
import neon.workstation.{Workstation, WorkstationEvent}

import java.time.Instant

/** Releases an active [[Workstation.Active]] back to [[Workstation.Idle]] when its assigned
  * consolidation group completes.
  */
object WorkstationReleasePolicy:

  /** Releases the workstation after consolidation group completion.
    *
    * @param event
    *   the consolidation group completion event
    * @param workstation
    *   the active workstation to release
    * @param at
    *   instant of the release
    * @return
    *   idle workstation and release event
    */
  def apply(
      event: ConsolidationGroupEvent.ConsolidationGroupCompleted,
      workstation: Workstation.Active,
      at: Instant
  ): (Workstation.Idle, WorkstationEvent.WorkstationReleased) =
    workstation.release(at)
