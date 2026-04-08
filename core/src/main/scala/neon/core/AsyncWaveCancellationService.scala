package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.WaveId
import neon.consolidationgroup.AsyncConsolidationGroupRepository
import neon.task.AsyncTaskRepository
import neon.transportorder.AsyncTransportOrderRepository
import neon.wave.{AsyncWaveRepository, Wave}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Async counterpart of [[WaveCancellationService]]. */
class AsyncWaveCancellationService(
    waveRepository: AsyncWaveRepository,
    taskRepository: AsyncTaskRepository,
    transportOrderRepository: AsyncTransportOrderRepository,
    consolidationGroupRepository: AsyncConsolidationGroupRepository
)(using ExecutionContext)
    extends LazyLogging:

  def cancel(
      waveId: WaveId,
      at: Instant
  ): Future[Either[WaveCancellationError, WaveCancellationResult]] =
    logger.debug("Starting wave cancellation for {}", waveId.value)
    waveRepository
      .findById(waveId)
      .flatMap:
        case None =>
          Future.successful(
            Left(WaveCancellationError.WaveNotFound(waveId))
          )
        case Some(planned: Wave.Planned) =>
          cancelPlanned(planned, at)
        case Some(released: Wave.Released) =>
          cancelReleased(released, at)
        case Some(_: Wave.Completed) | Some(_: Wave.Cancelled) =>
          Future.successful(
            Left(WaveCancellationError.WaveAlreadyTerminal(waveId))
          )

  private def cancelPlanned(
      planned: Wave.Planned,
      at: Instant
  ): Future[Either[WaveCancellationError, WaveCancellationResult]] =
    val (cancelled, cancelledEvent) = planned.cancel(at)
    waveRepository
      .save(cancelled, cancelledEvent)
      .map(_ => Right(WaveCancellationResult(cancelled, cancelledEvent, Nil, Nil, Nil)))

  private def cancelReleased(
      released: Wave.Released,
      at: Instant
  ): Future[Either[WaveCancellationError, WaveCancellationResult]] =
    val (cancelled, cancelledEvent) = released.cancel(at)
    for
      _ <- waveRepository.save(cancelled, cancelledEvent)
      waveTasks <- taskRepository.findByWaveId(released.id)
      cancelledTasks = TaskCancellationPolicy(waveTasks, at)
      _ <- taskRepository.saveAll(cancelledTasks)
      handlingUnitIds = waveTasks.flatMap(_.handlingUnitId).distinct
      transportOrders <- Future
        .traverse(handlingUnitIds)(
          transportOrderRepository.findByHandlingUnitId
        )
        .map(_.flatten)
      cancelledTransportOrders = TransportOrderCancellationPolicy(
        transportOrders,
        at
      )
      _ <- transportOrderRepository.saveAll(cancelledTransportOrders)
      consolidationGroups <- consolidationGroupRepository.findByWaveId(
        released.id
      )
      cancelledConsolidationGroups = ConsolidationGroupCancellationPolicy(
        consolidationGroups,
        at
      )
      _ <- consolidationGroupRepository.saveAll(cancelledConsolidationGroups)
    yield Right(
      WaveCancellationResult(
        cancelled = cancelled,
        cancelledEvent = cancelledEvent,
        cancelledTasks = cancelledTasks,
        cancelledTransportOrders = cancelledTransportOrders,
        cancelledConsolidationGroups = cancelledConsolidationGroups
      )
    )
