package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.{HandlingUnitId, TransportOrderId}
import neon.consolidationgroup.{
  AsyncConsolidationGroupRepository,
  ConsolidationGroup,
  ConsolidationGroupEvent
}
import neon.handlingunit.{AsyncHandlingUnitRepository, HandlingUnit}
import neon.task.AsyncTaskRepository
import neon.transportorder.{AsyncTransportOrderRepository, TransportOrder}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Async counterpart of [[TransportOrderConfirmationService]]. */
class AsyncTransportOrderConfirmationService(
    transportOrderRepository: AsyncTransportOrderRepository,
    handlingUnitRepository: AsyncHandlingUnitRepository,
    taskRepository: AsyncTaskRepository,
    consolidationGroupRepository: AsyncConsolidationGroupRepository
)(using ExecutionContext)
    extends LazyLogging:

  def confirm(
      transportOrderId: TransportOrderId,
      at: Instant
  ): Future[
    Either[
      TransportOrderConfirmationError,
      TransportOrderConfirmationResult
    ]
  ] =
    logger.debug(
      "Starting transport order confirmation for {}",
      transportOrderId.value
    )
    transportOrderRepository
      .findById(transportOrderId)
      .flatMap:
        case None =>
          Future.successful(
            Left(
              TransportOrderConfirmationError
                .TransportOrderNotFound(transportOrderId)
            )
          )
        case Some(pending: TransportOrder.Pending) =>
          confirmPending(pending, at)
        case Some(_) =>
          Future.successful(
            Left(
              TransportOrderConfirmationError
                .TransportOrderNotPending(transportOrderId)
            )
          )

  private def confirmPending(
      pending: TransportOrder.Pending,
      at: Instant
  ): Future[
    Either[
      TransportOrderConfirmationError,
      TransportOrderConfirmationResult
    ]
  ] =
    val (confirmed, confirmedEvent) = pending.confirm(at)
    transportOrderRepository
      .save(confirmed, confirmedEvent)
      .flatMap { _ =>
        handlingUnitRepository
          .findById(confirmed.handlingUnitId)
          .flatMap:
            case None =>
              Future.successful(
                Left(
                  TransportOrderConfirmationError
                    .HandlingUnitNotFound(confirmed.handlingUnitId)
                )
              )
            case Some(pickCreated: HandlingUnit.PickCreated) =>
              val (inBuffer, handlingUnitEvent) =
                BufferArrivalPolicy(confirmedEvent, pickCreated, at)
              handlingUnitRepository
                .save(inBuffer, handlingUnitEvent)
                .flatMap { _ =>
                  detectBufferCompletion(
                    confirmed.handlingUnitId,
                    inBuffer,
                    at
                  ).map { bufferCompletion =>
                    Right(
                      TransportOrderConfirmationResult(
                        confirmed = confirmed,
                        confirmedEvent = confirmedEvent,
                        handlingUnit = inBuffer,
                        handlingUnitEvent = handlingUnitEvent,
                        bufferCompletion = bufferCompletion
                      )
                    )
                  }
                }
            case Some(_) =>
              Future.successful(
                Left(
                  TransportOrderConfirmationError
                    .HandlingUnitNotPickCreated(confirmed.handlingUnitId)
                )
              )
      }

  private def detectBufferCompletion(
      handlingUnitId: HandlingUnitId,
      arrivedHandlingUnit: HandlingUnit.InBuffer,
      at: Instant
  ): Future[
    Option[
      (
          ConsolidationGroup.ReadyForWorkstation,
          ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation
      )
    ]
  ] =
    taskRepository.findByHandlingUnitId(handlingUnitId).flatMap { tasks =>
      tasks.find(_.waveId.isDefined) match
        case None       => Future.successful(None)
        case Some(task) =>
          val waveId = task.waveId.get
          val orderId = task.orderId
          consolidationGroupRepository.findByWaveId(waveId).flatMap { groups =>
            groups.collectFirst {
              case picked: ConsolidationGroup.Picked if picked.orderIds.contains(orderId) =>
                picked
            } match
              case None              => Future.successful(None)
              case Some(pickedGroup) =>
                checkBufferAndComplete(
                  pickedGroup,
                  waveId,
                  handlingUnitId,
                  arrivedHandlingUnit,
                  at
                )
          }
    }

  private def checkBufferAndComplete(
      pickedGroup: ConsolidationGroup.Picked,
      waveId: neon.common.WaveId,
      handlingUnitId: HandlingUnitId,
      arrivedHandlingUnit: HandlingUnit.InBuffer,
      at: Instant
  ): Future[
    Option[
      (
          ConsolidationGroup.ReadyForWorkstation,
          ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation
      )
    ]
  ] =
    val groupOrderIds = pickedGroup.orderIds.toSet
    for
      waveTasks <- taskRepository.findByWaveId(waveId)
      huIds = waveTasks
        .filter(t => groupOrderIds.contains(t.orderId))
        .flatMap(_.handlingUnitId)
        .distinct
      handlingUnits <- handlingUnitRepository.findByIds(huIds)
      updated = handlingUnits.map:
        case hu if hu.id == handlingUnitId => arrivedHandlingUnit
        case hu                            => hu
      result = BufferCompletionPolicy(updated, pickedGroup, at)
      _ <- result match
        case Some((ready, event)) =>
          consolidationGroupRepository.save(ready, event)
        case None => Future.unit
    yield result
