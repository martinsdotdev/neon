package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.TaskId
import neon.consolidationgroup.{
  AsyncConsolidationGroupRepository,
  ConsolidationGroup,
  ConsolidationGroupEvent
}
import neon.task.{AsyncTaskRepository, Task, TaskEvent}
import neon.transportorder.{AsyncTransportOrderRepository, TransportOrder, TransportOrderEvent}
import neon.wave.{AsyncWaveRepository, Wave, WaveEvent}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Async counterpart of [[TaskCompletionService]]. Orchestrates the 5-step post-completion cascade:
  * complete task, shortpick, routing, wave completion, picking completion.
  */
class AsyncTaskCompletionService(
    taskRepository: AsyncTaskRepository,
    waveRepository: AsyncWaveRepository,
    consolidationGroupRepository: AsyncConsolidationGroupRepository,
    transportOrderRepository: AsyncTransportOrderRepository,
    verificationProfile: VerificationProfile
)(using ExecutionContext)
    extends LazyLogging:

  def complete(
      taskId: TaskId,
      actualQuantity: Int,
      verified: Boolean,
      at: Instant
  ): Future[Either[TaskCompletionError, TaskCompletionResult]] =
    if actualQuantity < 0 then
      return Future.successful(
        Left(TaskCompletionError.InvalidActualQuantity(taskId, actualQuantity))
      )

    logger.debug("Starting task completion for {}", taskId.value)
    taskRepository
      .findById(taskId)
      .flatMap:
        case None =>
          Future.successful(Left(TaskCompletionError.TaskNotFound(taskId)))
        case Some(assigned: Task.Assigned) =>
          if verificationProfile.requiresVerification(
              assigned.packagingLevel
            ) && !verified
          then
            Future.successful(
              Left(TaskCompletionError.VerificationRequired(taskId))
            )
          else completeAssigned(assigned, actualQuantity, at)
        case Some(_) =>
          Future.successful(Left(TaskCompletionError.TaskNotAssigned(taskId)))

  private def completeAssigned(
      assigned: Task.Assigned,
      actualQuantity: Int,
      at: Instant
  ): Future[Either[TaskCompletionError, TaskCompletionResult]] =
    val (completed, completedEvent) = assigned.complete(actualQuantity, at)
    for
      _ <- taskRepository.save(completed, completedEvent)
      shortpick <- persistShortpick(completed, at)
      routing <- persistRouting(completedEvent, at)
      (waveCompletion, pickingCompletion) <- detectWaveAndPickingCompletion(
        completed,
        at
      )
    yield
      logger.info(
        "Task completed {} shortpick={} " +
          "transportOrder={} waveCompleted={} " +
          "pickingCompleted={}",
        completed.id.value,
        shortpick.isDefined: java.lang.Boolean,
        routing.isDefined: java.lang.Boolean,
        waveCompletion.isDefined: java.lang.Boolean,
        pickingCompletion.isDefined: java.lang.Boolean
      )
      Right(
        TaskCompletionResult(
          completed = completed,
          completedEvent = completedEvent,
          shortpick = shortpick,
          transportOrder = routing,
          waveCompletion = waveCompletion,
          pickingCompletion = pickingCompletion
        )
      )

  private def persistShortpick(
      completed: Task.Completed,
      at: Instant
  ): Future[Option[(Task.Planned, TaskEvent.TaskCreated)]] =
    ShortpickPolicy(completed, at) match
      case None                       => Future.successful(None)
      case Some((replacement, event)) =>
        taskRepository.save(replacement, event).map(_ => Some(replacement, event))

  private def persistRouting(
      completedEvent: TaskEvent.TaskCompleted,
      at: Instant
  ): Future[
    Option[(TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)]
  ] =
    RoutingPolicy(completedEvent, at) match
      case None                   => Future.successful(None)
      case Some((pending, event)) =>
        transportOrderRepository
          .save(pending, event)
          .map(_ => Some(pending, event))

  private def detectWaveAndPickingCompletion(
      completed: Task.Completed,
      at: Instant
  ): Future[
    (
        Option[(Wave.Completed, WaveEvent.WaveCompleted)],
        Option[
          (
              ConsolidationGroup.Picked,
              ConsolidationGroupEvent.ConsolidationGroupPicked
          )
        ]
    )
  ] =
    completed.waveId match
      case None         => Future.successful((None, None))
      case Some(waveId) =>
        for
          waveTasks <- taskRepository.findByWaveId(waveId)
          waveCompletion <- detectWaveCompletion(
            waveId,
            waveTasks,
            at
          )
          pickingCompletion <- detectPickingCompletion(
            waveId,
            waveTasks,
            completed,
            at
          )
        yield (waveCompletion, pickingCompletion)

  private def detectWaveCompletion(
      waveId: neon.common.WaveId,
      waveTasks: List[Task],
      at: Instant
  ): Future[Option[(Wave.Completed, WaveEvent.WaveCompleted)]] =
    waveRepository
      .findById(waveId)
      .flatMap:
        case Some(released: Wave.Released) =>
          WaveCompletionPolicy(waveTasks, released, at) match
            case None                         => Future.successful(None)
            case Some((completedWave, event)) =>
              waveRepository
                .save(completedWave, event)
                .map(_ => Some(completedWave, event))
        case _ => Future.successful(None)

  private def detectPickingCompletion(
      waveId: neon.common.WaveId,
      waveTasks: List[Task],
      completed: Task.Completed,
      at: Instant
  ): Future[
    Option[
      (
          ConsolidationGroup.Picked,
          ConsolidationGroupEvent.ConsolidationGroupPicked
      )
    ]
  ] =
    consolidationGroupRepository.findByWaveId(waveId).flatMap { groups =>
      groups.collectFirst {
        case cg: ConsolidationGroup.Created if cg.orderIds.contains(completed.orderId) =>
          cg
      } match
        case None     => Future.successful(None)
        case Some(cg) =>
          val groupOrderIds = cg.orderIds.toSet
          val groupTasks =
            waveTasks.filter(t => groupOrderIds.contains(t.orderId))
          PickingCompletionPolicy(groupTasks, cg, at) match
            case None                  => Future.successful(None)
            case Some((picked, event)) =>
              consolidationGroupRepository
                .save(picked, event)
                .map(_ => Some(picked, event))
    }
