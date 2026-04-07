package neon.core

import neon.common.{HandlingUnitId, WaveId}
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent,
  ConsolidationGroupRepository
}
import neon.task.{Task, TaskEvent, TaskRepository}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}
import neon.wave.{Wave, WaveEvent, WaveRepository}

import java.time.Instant

/** Errors that can occur during wave cancellation. */
sealed trait WaveCancellationError

object WaveCancellationError:
  /** The wave was not found in the repository. */
  case class WaveNotFound(waveId: WaveId) extends WaveCancellationError

  /** The wave is already in a terminal state ([[Wave.Completed]] or [[Wave.Cancelled]]).
    */
  case class WaveAlreadyTerminal(waveId: WaveId) extends WaveCancellationError

/** The result of a successful wave cancellation, containing the cancelled wave and all cascaded
  * cancellations.
  *
  * @param cancelled
  *   the cancelled wave
  * @param cancelledEvent
  *   the wave cancellation event
  * @param cancelledTasks
  *   tasks cancelled by the cascade
  * @param cancelledTransportOrders
  *   transport orders cancelled by the cascade
  * @param cancelledConsolidationGroups
  *   consolidation groups cancelled by the cascade
  */
case class WaveCancellationResult(
    cancelled: Wave.Cancelled,
    cancelledEvent: WaveEvent.WaveCancelled,
    cancelledTasks: List[(Task.Cancelled, TaskEvent.TaskCancelled)],
    cancelledTransportOrders: List[
      (TransportOrder.Cancelled, TransportOrderEvent.TransportOrderCancelled)
    ],
    cancelledConsolidationGroups: List[
      (ConsolidationGroup.Cancelled, ConsolidationGroupEvent.ConsolidationGroupCancelled)
    ]
)

/** Cancels a [[Wave.Planned]] or [[Wave.Released]] wave with cascading cancellation of downstream
  * aggregates.
  *
  * For Planned waves, cancels without cascade. For Released waves, cascades to open tasks, their
  * transport orders, and consolidation groups.
  *
  * @param waveRepository
  *   repository for wave lookup and persistence
  * @param taskRepository
  *   repository for task lookup and persistence
  * @param transportOrderRepository
  *   repository for transport order lookup and persistence
  * @param consolidationGroupRepository
  *   repository for consolidation group lookup and persistence
  */
class WaveCancellationService(
    waveRepository: WaveRepository,
    taskRepository: TaskRepository,
    transportOrderRepository: TransportOrderRepository,
    consolidationGroupRepository: ConsolidationGroupRepository
):
  /** Cancels a wave and cascades to downstream aggregates if released.
    *
    * For a [[Wave.Planned]] wave, cancels directly without cascade. For a [[Wave.Released]] wave,
    * cancels the wave and then cascades via [[TaskCancellationPolicy]],
    * [[TransportOrderCancellationPolicy]], and [[ConsolidationGroupCancellationPolicy]].
    *
    * @param waveId
    *   the wave to cancel
    * @param at
    *   instant of the cancellation
    * @return
    *   cancellation result or error
    */
  def cancel(
      waveId: WaveId,
      at: Instant
  ): Either[WaveCancellationError, WaveCancellationResult] =
    waveRepository.findById(waveId) match
      case None                          => Left(WaveCancellationError.WaveNotFound(waveId))
      case Some(planned: Wave.Planned)   => cancelPlanned(planned, at)
      case Some(released: Wave.Released) => cancelReleased(released, at)
      case Some(_: Wave.Completed)       => Left(WaveCancellationError.WaveAlreadyTerminal(waveId))
      case Some(_: Wave.Cancelled)       => Left(WaveCancellationError.WaveAlreadyTerminal(waveId))

  /** Cancels a planned wave directly without cascade. */
  private def cancelPlanned(
      planned: Wave.Planned,
      at: Instant
  ): Either[WaveCancellationError, WaveCancellationResult] =
    val (cancelled, cancelledEvent) = planned.cancel(at)
    waveRepository.save(cancelled, cancelledEvent)
    Right(WaveCancellationResult(cancelled, cancelledEvent, Nil, Nil, Nil))

  /** Cancels a released wave and cascades to tasks, transport orders, and consolidation groups.
    */
  private def cancelReleased(
      released: Wave.Released,
      at: Instant
  ): Either[WaveCancellationError, WaveCancellationResult] =
    val (cancelled, cancelledEvent) = released.cancel(at)
    waveRepository.save(cancelled, cancelledEvent)

    val waveTasks = taskRepository.findByWaveId(released.id)
    val cancelledTasks = TaskCancellationPolicy(waveTasks, at)
    taskRepository.saveAll(cancelledTasks)

    val handlingUnitIds = waveTasks.flatMap(_.handlingUnitId).distinct
    val transportOrders = handlingUnitIds.flatMap(transportOrderRepository.findByHandlingUnitId)
    val cancelledTransportOrders = TransportOrderCancellationPolicy(transportOrders, at)
    transportOrderRepository.saveAll(cancelledTransportOrders)

    val consolidationGroups = consolidationGroupRepository.findByWaveId(released.id)
    val cancelledConsolidationGroups = ConsolidationGroupCancellationPolicy(consolidationGroups, at)
    consolidationGroupRepository.saveAll(cancelledConsolidationGroups)

    Right(
      WaveCancellationResult(
        cancelled = cancelled,
        cancelledEvent = cancelledEvent,
        cancelledTasks = cancelledTasks,
        cancelledTransportOrders = cancelledTransportOrders,
        cancelledConsolidationGroups = cancelledConsolidationGroups
      )
    )
