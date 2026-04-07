package neon.core

import neon.common.{HandlingUnitId, TransportOrderId}
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent,
  ConsolidationGroupRepository
}
import neon.handlingunit.{HandlingUnit, HandlingUnitEvent, HandlingUnitRepository}
import neon.task.TaskRepository
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}

import java.time.Instant

/** Errors that can occur during transport order confirmation. */
sealed trait TransportOrderConfirmationError

object TransportOrderConfirmationError:
  /** The transport order was not found in the repository. */
  case class TransportOrderNotFound(transportOrderId: TransportOrderId)
      extends TransportOrderConfirmationError

  /** The transport order is not in the [[TransportOrder.Pending]] state required for confirmation.
    */
  case class TransportOrderNotPending(transportOrderId: TransportOrderId)
      extends TransportOrderConfirmationError

  /** The handling unit referenced by the transport order was not found. */
  case class HandlingUnitNotFound(handlingUnitId: HandlingUnitId)
      extends TransportOrderConfirmationError

  /** The handling unit is not in the [[HandlingUnit.PickCreated]] state required for buffer
    * arrival.
    */
  case class HandlingUnitNotPickCreated(handlingUnitId: HandlingUnitId)
      extends TransportOrderConfirmationError

/** The result of a successful transport order confirmation, containing the confirmed order, the
  * handling unit moved to buffer, and optional buffer completion for the consolidation group.
  *
  * @param confirmed
  *   the confirmed transport order
  * @param confirmedEvent
  *   the transport order confirmation event
  * @param handlingUnit
  *   the handling unit now in buffer
  * @param handlingUnitEvent
  *   the buffer arrival event
  * @param bufferCompletion
  *   consolidation group transition to [[ConsolidationGroup.ReadyForWorkstation]] if all handling
  *   units have arrived, [[None]] otherwise
  */
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

/** Confirms a [[TransportOrder.Pending]] transport order, triggering buffer arrival for its
  * handling unit and optionally detecting buffer completion for the consolidation group.
  *
  * @param transportOrderRepository
  *   repository for transport order lookup and persistence
  * @param handlingUnitRepository
  *   repository for handling unit lookup and persistence
  * @param taskRepository
  *   repository for task lookup
  * @param consolidationGroupRepository
  *   repository for consolidation group lookup and persistence
  */
class TransportOrderConfirmationService(
    transportOrderRepository: TransportOrderRepository,
    handlingUnitRepository: HandlingUnitRepository,
    taskRepository: TaskRepository,
    consolidationGroupRepository: ConsolidationGroupRepository
):
  /** Confirms a transport order and triggers the post-confirmation cascade.
    *
    * Steps: (1) confirm the [[TransportOrder.Pending]] transport order, (2) move the handling unit
    * to buffer via [[BufferArrivalPolicy]], (3) detect buffer completion for the consolidation
    * group via [[BufferCompletionPolicy]].
    *
    * @param transportOrderId
    *   the transport order to confirm
    * @param at
    *   instant of the confirmation
    * @return
    *   confirmation result or error
    */
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

  /** Confirms a pending transport order and moves the handling unit to buffer.
    */
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

  /** Detects whether all handling units for a consolidation group have arrived in the buffer,
    * transitioning the group to [[ConsolidationGroup.ReadyForWorkstation]] if so.
    */
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
