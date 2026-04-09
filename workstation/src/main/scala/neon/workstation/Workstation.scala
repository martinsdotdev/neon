package neon.workstation

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{WorkstationId, WorkstationMode}

import java.time.Instant
import java.util.UUID

/** Typestate-encoded workstation aggregate representing a physical station (put-wall or pack
  * station) where consolidation and packing operations occur.
  *
  * Lifecycle: [[Disabled]] -> [[Idle]] -> [[Active]] -> [[Idle]], with [[Disabled]] reachable from
  * both [[Idle]] and [[Active]]. One consolidation group at a time.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
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
      val mode = WorkstationMode.Picking
      val idle = Idle(id, workstationType, slotCount, mode)
      val event =
        WorkstationEvent.WorkstationEnabled(id, workstationType, slotCount, mode, at)
      (idle, event)

  /** A workstation that is enabled and ready to receive an assignment. */
  case class Idle(
      id: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int,
      mode: WorkstationMode
  ) extends Workstation:
    /** Switches the operational mode, transitioning from [[Idle]] to [[Idle]] with a new mode.
      *
      * @param newMode
      *   the mode to switch to
      * @param at
      *   instant of the transition
      * @return
      *   idle state with new mode and mode-switched event
      */
    def switchMode(
        newMode: WorkstationMode,
        at: Instant
    ): (Idle, WorkstationEvent.ModeSwitched) =
      val switched = copy(mode = newMode)
      val event =
        WorkstationEvent.ModeSwitched(id, workstationType, mode, newMode, at)
      (switched, event)

    /** Assigns work to this workstation, transitioning from [[Idle]] to [[Active]].
      *
      * @param assignmentId
      *   the mode-specific assignment identifier
      * @param at
      *   instant of the transition
      * @return
      *   active state and assigned event
      */
    def assign(
        assignmentId: UUID,
        at: Instant
    ): (Active, WorkstationEvent.WorkstationAssigned) =
      val active = Active(id, workstationType, slotCount, mode, assignmentId)
      val event =
        WorkstationEvent.WorkstationAssigned(id, workstationType, mode, assignmentId, at)
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

  /** A workstation actively processing an assignment. */
  case class Active(
      id: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int,
      mode: WorkstationMode,
      assignmentId: UUID
  ) extends Workstation:
    /** Releases the current assignment, transitioning from [[Active]] to [[Idle]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   idle state and released event
      */
    def release(at: Instant): (Idle, WorkstationEvent.WorkstationReleased) =
      val idle = Idle(id, workstationType, slotCount, mode)
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
