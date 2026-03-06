package neon.workstation

import neon.common.{ConsolidationGroupId, WorkstationId}

import java.time.Instant

/** Typestate-encoded workstation aggregate representing a physical station (put-wall or pack
  * station) where consolidation and packing operations occur.
  *
  * Lifecycle: [[Disabled]] -> [[Idle]] -> [[Active]] -> [[Idle]], with [[Disabled]] reachable from
  * both [[Idle]] and [[Active]]. One consolidation group at a time.
  */
sealed trait Workstation:
  /** The workstation identifier. */
  def id: WorkstationId

  /** The physical type of this workstation. */
  def workstationType: WorkstationType

  /** The number of slots available at this workstation. */
  def slotCount: Int

/** State definitions for [[Workstation]]. */
object Workstation:
  /** A workstation that is powered off or otherwise unavailable. */
  case class Disabled(
      id: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int
  ) extends Workstation:
    /** Enables this workstation, transitioning from [[Disabled]] to [[Idle]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   idle state and enabled event
      */
    def enable(at: Instant): (Idle, WorkstationEvent.WorkstationEnabled) =
      val idle = Idle(id, workstationType, slotCount)
      val event = WorkstationEvent.WorkstationEnabled(id, workstationType, slotCount, at)
      (idle, event)

  /** A workstation that is enabled and ready to receive a consolidation group. */
  case class Idle(
      id: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int
  ) extends Workstation:
    /** Assigns a consolidation group, transitioning from [[Idle]] to [[Active]].
      *
      * @param consolidationGroupId
      *   the consolidation group to assign
      * @param at
      *   instant of the transition
      * @return
      *   active state and assigned event
      */
    def assign(consolidationGroupId: ConsolidationGroupId, at: Instant): (Active, WorkstationEvent.WorkstationAssigned) =
      val active = Active(id, workstationType, slotCount, consolidationGroupId)
      val event = WorkstationEvent.WorkstationAssigned(id, workstationType, consolidationGroupId, at)
      (active, event)

    /** Disables this workstation, transitioning from [[Idle]] to [[Disabled]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   disabled state and disabled event
      */
    def disable(at: Instant): (Disabled, WorkstationEvent.WorkstationDisabled) =
      val disabled = Disabled(id, workstationType, slotCount)
      val event = WorkstationEvent.WorkstationDisabled(id, workstationType, at)
      (disabled, event)

  /** A workstation actively processing a consolidation group. */
  case class Active(
      id: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int,
      consolidationGroupId: ConsolidationGroupId
  ) extends Workstation:
    /** Releases the current consolidation group, transitioning from [[Active]] to [[Idle]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   idle state and released event
      */
    def release(at: Instant): (Idle, WorkstationEvent.WorkstationReleased) =
      val idle = Idle(id, workstationType, slotCount)
      val event = WorkstationEvent.WorkstationReleased(id, workstationType, at)
      (idle, event)

    /** Disables this workstation, transitioning from [[Active]] to [[Disabled]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   disabled state and disabled event
      */
    def disable(at: Instant): (Disabled, WorkstationEvent.WorkstationDisabled) =
      val disabled = Disabled(id, workstationType, slotCount)
      val event = WorkstationEvent.WorkstationDisabled(id, workstationType, at)
      (disabled, event)
