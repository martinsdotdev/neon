package neon.app

import neon.common.{GroupId, WorkstationId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.workstation.{Workstation, WorkstationEvent, WorkstationRepository}

import java.time.Instant

sealed trait ConsolidationGroupCompletionError

object ConsolidationGroupCompletionError:
  case class ConsolidationGroupNotFound(groupId: GroupId) extends ConsolidationGroupCompletionError
  case class ConsolidationGroupNotAssigned(groupId: GroupId)
      extends ConsolidationGroupCompletionError
  case class WorkstationNotFound(workstationId: WorkstationId)
      extends ConsolidationGroupCompletionError
  case class WorkstationNotActive(workstationId: WorkstationId)
      extends ConsolidationGroupCompletionError

case class ConsolidationGroupCompletionResult(
    completed: ConsolidationGroup.Completed,
    completedEvent: ConsolidationGroupEvent.ConsolidationGroupCompleted,
    workstation: Workstation.Idle,
    workstationEvent: WorkstationEvent.WorkstationReleased
)

class ConsolidationGroupCompletionService(
    consolidationGroupRepository: ConsolidationGroupRepository,
    workstationRepository: WorkstationRepository
):
  def complete(
      groupId: GroupId,
      at: Instant
  ): Either[ConsolidationGroupCompletionError, ConsolidationGroupCompletionResult] =
    consolidationGroupRepository.findById(groupId) match
      case None =>
        Left(ConsolidationGroupCompletionError.ConsolidationGroupNotFound(groupId))
      case Some(assigned: ConsolidationGroup.Assigned) =>
        completeAssigned(assigned, at)
      case Some(_) =>
        Left(ConsolidationGroupCompletionError.ConsolidationGroupNotAssigned(groupId))

  private def completeAssigned(
      assigned: ConsolidationGroup.Assigned,
      at: Instant
  ): Either[ConsolidationGroupCompletionError, ConsolidationGroupCompletionResult] =
    val (completed, completedEvent) = assigned.complete(at)
    consolidationGroupRepository.save(completed, completedEvent)

    workstationRepository.findById(assigned.workstationId) match
      case None =>
        Left(
          ConsolidationGroupCompletionError.WorkstationNotFound(assigned.workstationId)
        )
      case Some(active: Workstation.Active) =>
        val (idle, workstationEvent) = WorkstationReleasePolicy(completedEvent, active, at)
        workstationRepository.save(idle, workstationEvent)
        Right(
          ConsolidationGroupCompletionResult(
            completed = completed,
            completedEvent = completedEvent,
            workstation = idle,
            workstationEvent = workstationEvent
          )
        )
      case Some(_) =>
        Left(
          ConsolidationGroupCompletionError.WorkstationNotActive(assigned.workstationId)
        )
