package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.ConsolidationGroupId
import neon.consolidationgroup.{
  AsyncConsolidationGroupRepository,
  ConsolidationGroup,
  ConsolidationGroupEvent
}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait ConsolidationGroupCancellationError
object ConsolidationGroupCancellationError:
  case class ConsolidationGroupNotFound(id: ConsolidationGroupId)
      extends ConsolidationGroupCancellationError
  case class ConsolidationGroupAlreadyTerminal(id: ConsolidationGroupId)
      extends ConsolidationGroupCancellationError

case class ConsolidationGroupCancellationResult(
    cancelled: ConsolidationGroup.Cancelled,
    event: ConsolidationGroupEvent.ConsolidationGroupCancelled
)

class AsyncConsolidationGroupCancellationService(
    consolidationGroupRepository: AsyncConsolidationGroupRepository
)(using ExecutionContext)
    extends LazyLogging:

  def cancel(
      id: ConsolidationGroupId,
      at: Instant
  ): Future[Either[ConsolidationGroupCancellationError, ConsolidationGroupCancellationResult]] =
    consolidationGroupRepository
      .findById(id)
      .flatMap:
        case None =>
          Future.successful(
            Left(ConsolidationGroupCancellationError.ConsolidationGroupNotFound(id))
          )
        case Some(created: ConsolidationGroup.Created) =>
          val (cancelled, event) = created.cancel(at)
          consolidationGroupRepository
            .save(cancelled, event)
            .map(_ => Right(ConsolidationGroupCancellationResult(cancelled, event)))
        case Some(picked: ConsolidationGroup.Picked) =>
          val (cancelled, event) = picked.cancel(at)
          consolidationGroupRepository
            .save(cancelled, event)
            .map(_ => Right(ConsolidationGroupCancellationResult(cancelled, event)))
        case Some(ready: ConsolidationGroup.ReadyForWorkstation) =>
          val (cancelled, event) = ready.cancel(at)
          consolidationGroupRepository
            .save(cancelled, event)
            .map(_ => Right(ConsolidationGroupCancellationResult(cancelled, event)))
        case Some(assigned: ConsolidationGroup.Assigned) =>
          val (cancelled, event) = assigned.cancel(at)
          consolidationGroupRepository
            .save(cancelled, event)
            .map(_ => Right(ConsolidationGroupCancellationResult(cancelled, event)))
        case Some(_) =>
          Future.successful(
            Left(ConsolidationGroupCancellationError.ConsolidationGroupAlreadyTerminal(id))
          )
