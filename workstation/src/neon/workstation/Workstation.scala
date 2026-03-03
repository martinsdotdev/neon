package neon.workstation

import neon.common.{GroupId, WorkstationId}

import java.time.Instant

sealed trait Workstation:
  def id: WorkstationId
  def workstationType: WorkstationType
  def slotCount: Int

object Workstation:
  case class Disabled(
      id: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int
  ) extends Workstation:
    def enable(at: Instant): (Idle, WorkstationEvent.WorkstationEnabled) =
      val idle = Idle(id, workstationType, slotCount)
      val event = WorkstationEvent.WorkstationEnabled(id, workstationType, slotCount, at)
      (idle, event)

  case class Idle(
      id: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int
  ) extends Workstation:
    def assign(groupId: GroupId, at: Instant): (Active, WorkstationEvent.WorkstationAssigned) =
      val active = Active(id, workstationType, slotCount, groupId)
      val event = WorkstationEvent.WorkstationAssigned(id, workstationType, groupId, at)
      (active, event)

    def disable(at: Instant): (Disabled, WorkstationEvent.WorkstationDisabled) =
      val disabled = Disabled(id, workstationType, slotCount)
      val event = WorkstationEvent.WorkstationDisabled(id, workstationType, at)
      (disabled, event)

  case class Active(
      id: WorkstationId,
      workstationType: WorkstationType,
      slotCount: Int,
      groupId: GroupId
  ) extends Workstation:
    def release(at: Instant): (Idle, WorkstationEvent.WorkstationReleased) =
      val idle = Idle(id, workstationType, slotCount)
      val event = WorkstationEvent.WorkstationReleased(id, workstationType, at)
      (idle, event)

    def disable(at: Instant): (Disabled, WorkstationEvent.WorkstationDisabled) =
      val disabled = Disabled(id, workstationType, slotCount)
      val event = WorkstationEvent.WorkstationDisabled(id, workstationType, at)
      (disabled, event)
