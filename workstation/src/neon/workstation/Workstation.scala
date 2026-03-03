package neon.workstation

import neon.common.{GroupId, WorkstationId}

import java.time.Instant

sealed trait Workstation:
  def id: WorkstationId
  def workstationType: WorkstationType

object Workstation:
  case class Disabled(
      id: WorkstationId,
      workstationType: WorkstationType
  ) extends Workstation:
    def enable(at: Instant): (Idle, WorkstationEvent.WorkstationEnabled) =
      val idle = Idle(id, workstationType)
      val event = WorkstationEvent.WorkstationEnabled(id, workstationType, at)
      (idle, event)

  case class Idle(
      id: WorkstationId,
      workstationType: WorkstationType
  ) extends Workstation:
    def assign(groupId: GroupId, at: Instant): (Active, WorkstationEvent.WorkstationAssigned) =
      val active = Active(id, workstationType, groupId)
      val event = WorkstationEvent.WorkstationAssigned(id, workstationType, groupId, at)
      (active, event)

    def disable(at: Instant): (Disabled, WorkstationEvent.WorkstationDisabled) =
      val disabled = Disabled(id, workstationType)
      val event = WorkstationEvent.WorkstationDisabled(id, workstationType, at)
      (disabled, event)

  case class Active(
      id: WorkstationId,
      workstationType: WorkstationType,
      groupId: GroupId
  ) extends Workstation:
    def release(at: Instant): (Idle, WorkstationEvent.WorkstationReleased) =
      val idle = Idle(id, workstationType)
      val event = WorkstationEvent.WorkstationReleased(id, workstationType, at)
      (idle, event)

    def disable(at: Instant): (Disabled, WorkstationEvent.WorkstationDisabled) =
      val disabled = Disabled(id, workstationType)
      val event = WorkstationEvent.WorkstationDisabled(id, workstationType, at)
      (disabled, event)
