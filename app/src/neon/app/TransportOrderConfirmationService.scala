package neon.app

import neon.common.{HandlingUnitId, TransportOrderId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.handlingunit.{HandlingUnit, HandlingUnitEvent, HandlingUnitRepository}
import neon.task.TaskRepository
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}

import java.time.Instant

sealed trait TransportOrderConfirmationError

object TransportOrderConfirmationError:
  case class TransportOrderNotFound(transportOrderId: TransportOrderId)
      extends TransportOrderConfirmationError
  case class TransportOrderNotPending(transportOrderId: TransportOrderId)
      extends TransportOrderConfirmationError
  case class HandlingUnitNotFound(handlingUnitId: HandlingUnitId)
      extends TransportOrderConfirmationError
  case class HandlingUnitNotPickCreated(handlingUnitId: HandlingUnitId)
      extends TransportOrderConfirmationError

case class TransportOrderConfirmationResult(
    confirmed: TransportOrder.Confirmed,
    confirmedEvent: TransportOrderEvent.TransportOrderConfirmed,
    handlingUnit: HandlingUnit.InBuffer,
    handlingUnitEvent: HandlingUnitEvent.HandlingUnitMovedToBuffer,
    bufferCompletion: Option[
      (
          ConsolidationGroup.ReadyForWorkstation,
          ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation
      )
    ]
)

class TransportOrderConfirmationService(
    transportOrderRepository: TransportOrderRepository,
    handlingUnitRepository: HandlingUnitRepository,
    taskRepository: TaskRepository,
    consolidationGroupRepository: ConsolidationGroupRepository
):
  def confirm(
      transportOrderId: TransportOrderId,
      at: Instant
  ): Either[TransportOrderConfirmationError, TransportOrderConfirmationResult] =
    transportOrderRepository.findById(transportOrderId) match
      case None =>
        Left(TransportOrderConfirmationError.TransportOrderNotFound(transportOrderId))
      case Some(pending: TransportOrder.Pending) =>
        confirmPending(pending, at)
      case Some(_) =>
        Left(TransportOrderConfirmationError.TransportOrderNotPending(transportOrderId))

  private def confirmPending(
      pending: TransportOrder.Pending,
      at: Instant
  ): Either[TransportOrderConfirmationError, TransportOrderConfirmationResult] =
    val (confirmed, confirmedEvent) = pending.confirm(at)
    transportOrderRepository.save(confirmed, confirmedEvent)

    handlingUnitRepository.findById(confirmed.handlingUnitId) match
      case None =>
        Left(
          TransportOrderConfirmationError.HandlingUnitNotFound(confirmed.handlingUnitId)
        )
      case Some(pickCreated: HandlingUnit.PickCreated) =>
        val (inBuffer, handlingUnitEvent) = BufferArrivalPolicy(confirmedEvent, pickCreated, at)
        handlingUnitRepository.save(inBuffer, handlingUnitEvent)

        val bufferCompletion = detectBufferCompletion(confirmed.handlingUnitId, inBuffer, at)

        Right(
          TransportOrderConfirmationResult(
            confirmed = confirmed,
            confirmedEvent = confirmedEvent,
            handlingUnit = inBuffer,
            handlingUnitEvent = handlingUnitEvent,
            bufferCompletion = bufferCompletion
          )
        )
      case Some(_) =>
        Left(
          TransportOrderConfirmationError.HandlingUnitNotPickCreated(confirmed.handlingUnitId)
        )

  private def detectBufferCompletion(
      handlingUnitId: HandlingUnitId,
      arrivedHandlingUnit: HandlingUnit.InBuffer,
      at: Instant
  ): Option[
    (
        ConsolidationGroup.ReadyForWorkstation,
        ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation
    )
  ] =
    val taskWithWave = taskRepository
      .findByHandlingUnitId(handlingUnitId)
      .find(_.waveId.isDefined)

    taskWithWave.flatMap { task =>
      val waveId = task.waveId.get
      val orderId = task.orderId

      consolidationGroupRepository
        .findByWaveId(waveId)
        .collectFirst {
          case picked: ConsolidationGroup.Picked if picked.orderIds.contains(orderId) =>
            picked
        }
        .flatMap { pickedConsolidationGroup =>
          val consolidationGroupOrderIds = pickedConsolidationGroup.orderIds.toSet
          val waveTasks = taskRepository.findByWaveId(waveId)
          val consolidationGroupHandlingUnitIds = waveTasks
            .filter(t => consolidationGroupOrderIds.contains(t.orderId))
            .flatMap(_.handlingUnitId)
            .distinct

          val handlingUnits = handlingUnitRepository.findByIds(consolidationGroupHandlingUnitIds)
          val updatedHandlingUnits = handlingUnits.map {
            case hu if hu.id == handlingUnitId => arrivedHandlingUnit
            case hu                            => hu
          }

          val result = BufferCompletionPolicy(updatedHandlingUnits, pickedConsolidationGroup, at)
          result.foreach { (readyConsolidationGroup, event) =>
            consolidationGroupRepository.save(readyConsolidationGroup, event)
          }
          result
        }
    }
