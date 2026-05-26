package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.ConsolidationGroupId
import neon.consolidationgroup.{AsyncConsolidationGroupRepository, ConsolidationGroup}
import neon.workstation.{AsyncWorkstationRepository, Workstation}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Async counterpart of [[ConsolidationGroupCompletionService]]. */
class AsyncConsolidationGroupCompletionService(
    consolidationGroupRepository: AsyncConsolidationGroupRepository,
    workstationRepository: AsyncWorkstationRepository
)(using ExecutionContext)
    extends LazyLogging:

  /** Advances a [[ConsolidationGroup.Picked]] to [[ConsolidationGroup.ReadyForWorkstation]]. Closes
    * the previously HTTP-unreachable gap between picking completion and workstation assignment.
    */
  def markReadyForWorkstation(
      consolidationGroupId: ConsolidationGroupId,
      at: Instant
  ): Future[
    Either[
      ConsolidationGroupAdvanceError,
      ConsolidationGroupAdvanceResult
    ]
  ] =
    logger.debug(
      "Marking consolidation group ready for workstation {}",
      consolidationGroupId.value
    )
    consolidationGroupRepository
      .findById(consolidationGroupId)
      .flatMap:
        case None =>
          Future.successful(
            Left(
              ConsolidationGroupAdvanceError
                .ConsolidationGroupNotFound(consolidationGroupId)
            )
          )
        case Some(picked: ConsolidationGroup.Picked) =>
          val (ready, event) = picked.readyForWorkstation(at)
          consolidationGroupRepository
            .save(ready, event)
            .map(_ => Right(ConsolidationGroupAdvanceResult(ready, event)))
        case Some(_) =>
          Future.successful(
            Left(
              ConsolidationGroupAdvanceError
                .ConsolidationGroupNotPicked(consolidationGroupId)
            )
          )

  def complete(
      consolidationGroupId: ConsolidationGroupId,
      at: Instant
  ): Future[
    Either[
      ConsolidationGroupCompletionError,
      ConsolidationGroupCompletionResult
    ]
  ] =
    logger.debug(
      "Starting consolidation group completion for {}",
      consolidationGroupId.value
    )
    consolidationGroupRepository
      .findById(consolidationGroupId)
      .flatMap:
        case None =>
          Future.successful(
            Left(
              ConsolidationGroupCompletionError
                .ConsolidationGroupNotFound(consolidationGroupId)
            )
          )
        case Some(assigned: ConsolidationGroup.Assigned) =>
          completeAssigned(assigned, at)
        case Some(_) =>
          Future.successful(
            Left(
              ConsolidationGroupCompletionError
                .ConsolidationGroupNotAssigned(consolidationGroupId)
            )
          )

  private def completeAssigned(
      assigned: ConsolidationGroup.Assigned,
      at: Instant
  ): Future[
    Either[
      ConsolidationGroupCompletionError,
      ConsolidationGroupCompletionResult
    ]
  ] =
    workstationRepository
      .findById(assigned.workstationId)
      .flatMap:
        case None =>
          Future.successful(
            Left(
              ConsolidationGroupCompletionError
                .WorkstationNotFound(assigned.workstationId)
            )
          )
        case Some(active: Workstation.Active) =>
          val (completed, completedEvent) = assigned.complete(at)
          val (idle, workstationEvent) =
            WorkstationReleasePolicy(completedEvent, active, at)
          for
            _ <- consolidationGroupRepository.save(completed, completedEvent)
            _ <- workstationRepository.save(idle, workstationEvent)
          yield Right(
            ConsolidationGroupCompletionResult(
              completed = completed,
              completedEvent = completedEvent,
              workstation = idle,
              workstationEvent = workstationEvent
            )
          )
        case Some(_) =>
          Future.successful(
            Left(
              ConsolidationGroupCompletionError
                .WorkstationNotActive(assigned.workstationId)
            )
          )
