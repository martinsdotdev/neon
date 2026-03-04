package neon.app

import neon.common.GroupId
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.workstation.{Workstation, WorkstationEvent, WorkstationType, WorkstationRepository}

import java.time.Instant

sealed trait WorkstationAssignmentError

object WorkstationAssignmentError:
  case class ConsolidationGroupNotFound(groupId: GroupId) extends WorkstationAssignmentError
  case class ConsolidationGroupNotReady(groupId: GroupId) extends WorkstationAssignmentError
  case class NoWorkstationAvailable(groupId: GroupId) extends WorkstationAssignmentError

case class WorkstationAssignmentResult(
    consolidationGroup: ConsolidationGroup.Assigned,
    consolidationGroupEvent: ConsolidationGroupEvent.ConsolidationGroupAssigned,
    workstation: Workstation.Active,
    workstationEvent: WorkstationEvent.WorkstationAssigned
)

class WorkstationAssignmentService(
    consolidationGroupRepository: ConsolidationGroupRepository,
    workstationRepository: WorkstationRepository
):
  def assign(
      groupId: GroupId,
      at: Instant
  ): Either[WorkstationAssignmentError, WorkstationAssignmentResult] =
    consolidationGroupRepository.findById(groupId) match
      case None =>
        Left(WorkstationAssignmentError.ConsolidationGroupNotFound(groupId))
      case Some(ready: ConsolidationGroup.ReadyForWorkstation) =>
        assignToWorkstation(ready, at)
      case Some(_) =>
        Left(WorkstationAssignmentError.ConsolidationGroupNotReady(groupId))

  private def assignToWorkstation(
      ready: ConsolidationGroup.ReadyForWorkstation,
      at: Instant
  ): Either[WorkstationAssignmentError, WorkstationAssignmentResult] =
    workstationRepository.findIdleByType(WorkstationType.PutWall) match
      case None =>
        Left(WorkstationAssignmentError.NoWorkstationAvailable(ready.id))
      case Some(idle) =>
        WorkstationAssignmentPolicy(ready, idle, at) match
          case None =>
            Left(WorkstationAssignmentError.NoWorkstationAvailable(ready.id))
          case Some(
                (
                  (assignedConsolidationGroup, consolidationGroupEvent),
                  (activeWorkstation, workstationEvent)
                )
              ) =>
            consolidationGroupRepository.save(assignedConsolidationGroup, consolidationGroupEvent)
            workstationRepository.save(activeWorkstation, workstationEvent)
            Right(
              WorkstationAssignmentResult(
                consolidationGroup = assignedConsolidationGroup,
                consolidationGroupEvent = consolidationGroupEvent,
                workstation = activeWorkstation,
                workstationEvent = workstationEvent
              )
            )
