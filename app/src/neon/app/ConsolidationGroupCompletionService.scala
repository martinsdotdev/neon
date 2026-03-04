package neon.app

import neon.common.{GroupId, WorkstationId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.workstation.{Workstation, WorkstationEvent, WorkstationRepository}

import java.time.Instant

/** Errors that can occur during consolidation group completion. */
sealed trait ConsolidationGroupCompletionError

object ConsolidationGroupCompletionError:
  /** The consolidation group was not found in the repository. */
  case class ConsolidationGroupNotFound(groupId: GroupId) extends ConsolidationGroupCompletionError

  /** The consolidation group is not in the [[ConsolidationGroup.Assigned]] state required for
    * completion.
    */
  case class ConsolidationGroupNotAssigned(groupId: GroupId)
      extends ConsolidationGroupCompletionError

  /** The workstation referenced by the consolidation group was not found. */
  case class WorkstationNotFound(workstationId: WorkstationId)
      extends ConsolidationGroupCompletionError

  /** The workstation is not in the [[Workstation.Active]] state required for release.
    */
  case class WorkstationNotActive(workstationId: WorkstationId)
      extends ConsolidationGroupCompletionError

/** The result of a successful consolidation group completion, containing the completed group and
  * the released workstation.
  *
  * @param completed
  *   the completed consolidation group
  * @param completedEvent
  *   the consolidation group completion event
  * @param workstation
  *   the workstation returned to idle
  * @param workstationEvent
  *   the workstation release event
  */
case class ConsolidationGroupCompletionResult(
    completed: ConsolidationGroup.Completed,
    completedEvent: ConsolidationGroupEvent.ConsolidationGroupCompleted,
    workstation: Workstation.Idle,
    workstationEvent: WorkstationEvent.WorkstationReleased
)

/** Completes a [[ConsolidationGroup.Assigned]] consolidation group and releases its workstation
  * back to idle.
  *
  * @param consolidationGroupRepository
  *   repository for consolidation group lookup and persistence
  * @param workstationRepository
  *   repository for workstation lookup and persistence
  */
class ConsolidationGroupCompletionService(
    consolidationGroupRepository: ConsolidationGroupRepository,
    workstationRepository: WorkstationRepository
):
  /** Completes a consolidation group and releases its workstation.
    *
    * Steps: (1) complete the [[ConsolidationGroup.Assigned]] group, (2) release the
    * [[Workstation.Active]] workstation back to idle via [[WorkstationReleasePolicy]].
    *
    * @param groupId
    *   the consolidation group to complete
    * @param at
    *   instant of the completion
    * @return
    *   completion result or error
    */
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

  /** Completes the assigned group and releases the workstation via [[WorkstationReleasePolicy]].
    */
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
