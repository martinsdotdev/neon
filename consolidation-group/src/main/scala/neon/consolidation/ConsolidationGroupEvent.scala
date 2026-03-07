package neon.consolidationgroup

import neon.common.{ConsolidationGroupId, OrderId, WaveId, WorkstationId}

import java.time.Instant

/** Domain events emitted by [[ConsolidationGroup]] state transitions. */
sealed trait ConsolidationGroupEvent:
  /** The consolidation group that emitted this event. */
  def consolidationGroupId: ConsolidationGroupId

  /** The wave that originated the consolidation group. */
  def waveId: WaveId

  /** The instant at which this event occurred. */
  def occurredAt: Instant

/** Event case classes for the [[ConsolidationGroup]] aggregate. */
object ConsolidationGroupEvent:

  /** Emitted when a consolidation group is created for a set of orders. */
  case class ConsolidationGroupCreated(
      consolidationGroupId: ConsolidationGroupId,
      waveId: WaveId,
      orderIds: List[OrderId],
      occurredAt: Instant
  ) extends ConsolidationGroupEvent

  /** Emitted when all picks for a consolidation group are complete. */
  case class ConsolidationGroupPicked(
      consolidationGroupId: ConsolidationGroupId,
      waveId: WaveId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent

  /** Emitted when all buffered units have arrived and the group is ready for workstation
    * assignment.
    */
  case class ConsolidationGroupReadyForWorkstation(
      consolidationGroupId: ConsolidationGroupId,
      waveId: WaveId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent

  /** Emitted when a consolidation group is assigned to a workstation. */
  case class ConsolidationGroupAssigned(
      consolidationGroupId: ConsolidationGroupId,
      waveId: WaveId,
      workstationId: WorkstationId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent

  /** Emitted when workstation processing of a consolidation group finishes.
    */
  case class ConsolidationGroupCompleted(
      consolidationGroupId: ConsolidationGroupId,
      waveId: WaveId,
      workstationId: WorkstationId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent

  /** Emitted when a consolidation group is cancelled from any non-terminal state.
    */
  case class ConsolidationGroupCancelled(
      consolidationGroupId: ConsolidationGroupId,
      waveId: WaveId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent
