package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.TaskId
import neon.consolidationgroup.{AsyncConsolidationGroupRepository, ConsolidationGroup}
import neon.core.TaskCompletionCascade.CascadeState
import neon.stockposition.AsyncStockPositionRepository
import neon.task.{AsyncTaskRepository, Task}
import neon.transportorder.AsyncTransportOrderRepository
import neon.wave.{AsyncWaveRepository, Wave}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Async counterpart of [[TaskCompletionService]]: loads the cascade state, delegates every
  * decision to [[TaskCompletionCascade]], and persists the outcome.
  *
  * Persistence fans out to individual entity actors and is not transactional: a failure mid-cascade
  * leaves earlier saves in place. Stock writes persist sequentially because the second command of a
  * partial pick (deallocate) requires the first (consume) to have been applied to the actor.
  */
class AsyncTaskCompletionService(
    taskRepository: AsyncTaskRepository,
    waveRepository: AsyncWaveRepository,
    consolidationGroupRepository: AsyncConsolidationGroupRepository,
    transportOrderRepository: AsyncTransportOrderRepository,
    stockPositionRepository: AsyncStockPositionRepository,
    verificationProfile: VerificationProfile
)(using ExecutionContext)
    extends LazyLogging:

  def complete(
      taskId: TaskId,
      actualQuantity: Int,
      verified: Boolean,
      at: Instant
  ): Future[Either[TaskCompletionError, TaskCompletionResult]] =
    logger.debug("Starting task completion for {}", taskId.value)
    taskRepository.findById(taskId).flatMap { task =>
      TaskCompletionCascade.validate(
        taskId = taskId,
        task = task,
        actualQuantity = actualQuantity,
        verified = verified,
        verificationProfile = verificationProfile
      ) match
        case Left(error)     => Future.successful(Left(error))
        case Right(assigned) =>
          for
            state <- loadCascadeState(assigned)
            outcome = TaskCompletionCascade.decide(
              assigned = assigned,
              actualQuantity = actualQuantity,
              at = at,
              state = state
            )
            _ <- persist(outcome)
          yield
            logCompletion(outcome)
            Right(outcome.result)
    }

  /** Loads everything the cascade needs. All loads happen before the decision. */
  private def loadCascadeState(assigned: Task.Assigned): Future[CascadeState] =
    val stockPositionLoad = assigned.stockPositionId match
      case None                  => Future.successful(None)
      case Some(stockPositionId) => stockPositionRepository.findById(stockPositionId)
    val waveLoad = assigned.waveId match
      case None =>
        Future.successful((Option.empty[Wave], List.empty[Task], List.empty[ConsolidationGroup]))
      case Some(waveId) =>
        for
          wave <- waveRepository.findById(waveId)
          waveTasks <- taskRepository.findByWaveId(waveId)
          consolidationGroups <- consolidationGroupRepository.findByWaveId(waveId)
        yield (wave, waveTasks, consolidationGroups)
    for
      stockPosition <- stockPositionLoad
      (wave, waveTasks, consolidationGroups) <- waveLoad
    yield CascadeState(
      stockPosition = stockPosition,
      wave = wave,
      waveTasks = waveTasks,
      consolidationGroups = consolidationGroups
    )

  /** Persists the outcome in cascade order: task, stock writes, shortpick replacement, transport
    * order, wave completion, picking completion.
    */
  private def persist(outcome: TaskCompletionCascade.Outcome): Future[Unit] =
    def saveOption[A, E](entry: Option[(A, E)])(save: (A, E) => Future[Unit]): Future[Unit] =
      entry.fold(Future.unit) { (value, event) => save(value, event) }
    for
      _ <- taskRepository.save(outcome.result.completed, outcome.result.completedEvent)
      _ <- outcome.stockWrites.foldLeft(Future.unit) { (previous, write) =>
        val (position, event) = write
        previous.flatMap(_ => stockPositionRepository.save(position, event))
      }
      _ <- saveOption(outcome.result.shortpick)(taskRepository.save)
      _ <- saveOption(outcome.result.transportOrder)(transportOrderRepository.save)
      _ <- saveOption(outcome.result.waveCompletion)(waveRepository.save)
      _ <- saveOption(outcome.result.pickingCompletion)(consolidationGroupRepository.save)
    yield ()

  private def logCompletion(outcome: TaskCompletionCascade.Outcome): Unit =
    logger.info(
      "Task completed {} shortpick={} " +
        "transportOrder={} waveCompleted={} " +
        "pickingCompleted={} stockWrites={}",
      outcome.result.completed.id.value,
      outcome.result.shortpick.isDefined: java.lang.Boolean,
      outcome.result.transportOrder.isDefined: java.lang.Boolean,
      outcome.result.waveCompletion.isDefined: java.lang.Boolean,
      outcome.result.pickingCompletion.isDefined: java.lang.Boolean,
      outcome.stockWrites.size: java.lang.Integer
    )
