package neon.workstation

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import neon.common.serialization.CborSerializable
import neon.common.{WorkstationId, WorkstationMode}

import java.time.Instant
import java.util.UUID

/** Domain events emitted by [[Workstation]] state transitions. */
sealed trait WorkstationEvent extends CborSerializable:
  /** The workstation that emitted this event. */
  def workstationId: WorkstationId

  /** The physical type of the workstation. */
  def workstationType: WorkstationType

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for [[Workstation]]. */
object WorkstationEvent:
  /** Emitted when a workstation transitions from [[Workstation.Disabled]] to [[Workstation.Idle]].
    *
    * @param workstationId
    *   the workstation identifier
    * @param workstationType
    *   the physical type of the workstation
    * @param slotCount
    *   the number of slots at this workstation
    * @param mode
    *   the operational mode assigned on enable
    * @param occurredAt
    *   instant of the transition
    */
  @JsonIgnoreProperties(ignoreUnknown = true)
  case class WorkstationEnabled(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int,
      mode: WorkstationMode,
      occurredAt: Instant
  ) extends WorkstationEvent

  /** Emitted when an assignment is made to a workstation.
    *
    * @param workstationId
    *   the workstation identifier
    * @param workstationType
    *   the physical type of the workstation
    * @param mode
    *   the operational mode of the workstation at assignment time
    * @param assignmentId
    *   the mode-specific assignment identifier
    * @param occurredAt
    *   instant of the transition
    */
  @JsonIgnoreProperties(ignoreUnknown = true)
  case class WorkstationAssigned(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      mode: WorkstationMode,
      assignmentId: UUID,
      occurredAt: Instant
  ) extends WorkstationEvent

  /** Emitted when a workstation switches its operational mode while in [[Workstation.Idle]].
    *
    * @param workstationId
    *   the workstation identifier
    * @param workstationType
    *   the physical type of the workstation
    * @param previousMode
    *   the mode before the switch
    * @param newMode
    *   the mode after the switch
    * @param occurredAt
    *   instant of the transition
    */
  case class ModeSwitched(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      previousMode: WorkstationMode,
      newMode: WorkstationMode,
      occurredAt: Instant
  ) extends WorkstationEvent

  /** Emitted when a workstation releases its assignment and returns to [[Workstation.Idle]].
    *
    * @param workstationId
    *   the workstation identifier
    * @param workstationType
    *   the physical type of the workstation
    * @param occurredAt
    *   instant of the transition
    */
  case class WorkstationReleased(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      occurredAt: Instant
  ) extends WorkstationEvent

  /** Emitted when a workstation is disabled from either [[Workstation.Idle]] or
    * [[Workstation.Active]].
    *
    * @param workstationId
    *   the workstation identifier
    * @param workstationType
    *   the physical type of the workstation
    * @param occurredAt
    *   instant of the transition
    */
  case class WorkstationDisabled(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      occurredAt: Instant
  ) extends WorkstationEvent
