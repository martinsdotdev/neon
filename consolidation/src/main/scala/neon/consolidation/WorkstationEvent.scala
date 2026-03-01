package neon.consolidationgroup

import neon.common.{GroupId, WorkstationId}
import java.time.Instant

sealed trait WorkstationEvent:
  def workstationId: WorkstationId
  def workstationType: WorkstationType
  def occurredAt: Instant

object WorkstationEvent:
  case class WorkstationEnabled(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      occurredAt: Instant
  ) extends WorkstationEvent

  case class WorkstationAssigned(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      groupId: GroupId,
      occurredAt: Instant
  ) extends WorkstationEvent

  case class WorkstationReleased(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      occurredAt: Instant
  ) extends WorkstationEvent

  case class WorkstationDisabled(
      workstationId: WorkstationId,
      workstationType: WorkstationType,
      occurredAt: Instant
  ) extends WorkstationEvent
