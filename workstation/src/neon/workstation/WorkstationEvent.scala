package neon.workstation

import neon.common.{ConsolidationGroupId, WorkstationId}

import java.time.Instant

/** Domain events emitted by [[Workstation]] state transitions. */
sealed trait WorkstationEvent:
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
    * @param occurredAt
    *   instant of the transition
    */
  case class WorkstationEnabled(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int,
      occurredAt: Instant
  ) extends WorkstationEvent

  /** Emitted when a consolidation group is assigned to a workstation.
    *
    * @param workstationId
    *   the workstation identifier
    * @param workstationType
    *   the physical type of the workstation
    * @param consolidationGroupId
    *   the assigned consolidation group
    * @param occurredAt
    *   instant of the transition
    */
  case class WorkstationAssigned(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      consolidationGroupId: ConsolidationGroupId,
      occurredAt: Instant
  ) extends WorkstationEvent

  /** Emitted when a workstation releases its consolidation group and returns to
    * [[Workstation.Idle]].
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
