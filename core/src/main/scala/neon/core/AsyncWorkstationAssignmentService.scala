package neon.core

import neon.common.ConsolidationGroupId
import neon.consolidationgroup.{AsyncConsolidationGroupRepository, ConsolidationGroup}
import neon.workstation.{AsyncWorkstationRepository, WorkstationType}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Async counterpart of [[WorkstationAssignmentService]]. */
class AsyncWorkstationAssignmentService(
    consolidationGroupRepository: AsyncConsolidationGroupRepository,
    workstationRepository: AsyncWorkstationRepository
)(using ExecutionContext):

  def assign(
      consolidationGroupId: ConsolidationGroupId,
      at: Instant
  ): Future[Either[WorkstationAssignmentError, WorkstationAssignmentResult]] =
    consolidationGroupRepository
      .findById(consolidationGroupId)
      .flatMap:
        case None =>
          Future.successful(
            Left(
              WorkstationAssignmentError
                .ConsolidationGroupNotFound(consolidationGroupId)
            )
          )
        case Some(ready: ConsolidationGroup.ReadyForWorkstation) =>
          assignToWorkstation(ready, at)
        case Some(_) =>
          Future.successful(
            Left(
              WorkstationAssignmentError
                .ConsolidationGroupNotReady(consolidationGroupId)
            )
          )

  private def assignToWorkstation(
      ready: ConsolidationGroup.ReadyForWorkstation,
      at: Instant
  ): Future[
    Either[WorkstationAssignmentError, WorkstationAssignmentResult]
  ] =
    workstationRepository
      .findIdleByType(WorkstationType.PutWall)
      .flatMap:
        case None =>
          Future.successful(
            Left(WorkstationAssignmentError.NoWorkstationAvailable(ready.id))
          )
        case Some(idle) =>
          WorkstationAssignmentPolicy(ready, idle, at) match
            case None =>
              Future.successful(
                Left(
                  WorkstationAssignmentError.NoWorkstationAvailable(ready.id)
                )
              )
            case Some(
                  (
                    (assignedGroup, groupEvent),
                    (activeWorkstation, workstationEvent)
                  )
                ) =>
              for
                _ <- consolidationGroupRepository
                  .save(assignedGroup, groupEvent)
                _ <- workstationRepository
                  .save(activeWorkstation, workstationEvent)
              yield Right(
                WorkstationAssignmentResult(
                  consolidationGroup = assignedGroup,
                  consolidationGroupEvent = groupEvent,
                  workstation = activeWorkstation,
                  workstationEvent = workstationEvent
                )
              )
