package neon.consolidationgroup

import neon.common.{GroupId, WaveId, WorkstationId}
import java.time.Instant

sealed trait ConsolidationGroupEvent:
  def groupId: GroupId
  def waveId: WaveId
  def occurredAt: Instant

object ConsolidationGroupEvent:
  case class ConsolidationGroupPicked(
      groupId: GroupId,
      waveId: WaveId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent

  case class ConsolidationGroupReadyForWorkstation(
      groupId: GroupId,
      waveId: WaveId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent

  case class ConsolidationGroupAssigned(
      groupId: GroupId,
      waveId: WaveId,
      workstationId: WorkstationId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent

  case class ConsolidationGroupCompleted(
      groupId: GroupId,
      waveId: WaveId,
      workstationId: WorkstationId,
      occurredAt: Instant
  ) extends ConsolidationGroupEvent
