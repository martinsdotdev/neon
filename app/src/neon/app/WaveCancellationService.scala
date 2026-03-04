package neon.app

import neon.common.{HandlingUnitId, WaveId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.task.{Task, TaskEvent, TaskRepository}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}
import neon.wave.{Wave, WaveEvent, WaveRepository}

import java.time.Instant

sealed trait WaveCancellationError

object WaveCancellationError:
  case class WaveNotFound(waveId: WaveId) extends WaveCancellationError
  case class WaveAlreadyTerminal(waveId: WaveId) extends WaveCancellationError

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

class WaveCancellationService(
    waveRepository: WaveRepository,
    taskRepository: TaskRepository,
    transportOrderRepository: TransportOrderRepository,
    consolidationGroupRepository: ConsolidationGroupRepository
):
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

  private def cancelPlanned(
      planned: Wave.Planned,
      at: Instant
  ): Either[WaveCancellationError, WaveCancellationResult] =
    val (cancelled, cancelledEvent) = planned.cancel(at)
    waveRepository.save(cancelled, cancelledEvent)
    Right(WaveCancellationResult(cancelled, cancelledEvent, Nil, Nil, Nil))

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
