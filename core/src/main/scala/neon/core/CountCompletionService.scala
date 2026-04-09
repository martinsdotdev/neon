package neon.core

import neon.common.CycleCountId
import neon.counttask.{CountTask, CountTaskRepository}
import neon.cyclecount.{CycleCount, CycleCountEvent, CycleCountRepository}

import java.time.Instant

/** Errors that can occur during count completion. */
sealed trait CountCompletionError

object CountCompletionError:
  /** The cycle count was not found in the repository. */
  case class CycleCountNotFound(cycleCountId: CycleCountId) extends CountCompletionError

  /** The cycle count is not in the [[CycleCount.InProgress]] state required for completion. */
  case class CycleCountNotInProgress(cycleCountId: CycleCountId) extends CountCompletionError

  /** Not all count tasks are in a terminal state (Recorded or Cancelled). */
  case class OpenCountTasksRemaining(cycleCountId: CycleCountId) extends CountCompletionError

/** The result of a successful count completion.
  *
  * @param completed
  *   the completed cycle count
  * @param completedEvent
  *   the cycle count completion event
  * @param variances
  *   variance records for count tasks with non-zero differences
  */
case class CountCompletionResult(
    completed: CycleCount.Completed,
    completedEvent: CycleCountEvent.CycleCountCompleted,
    variances: List[CountVariance]
)

/** Orchestrates cycle count completion: verifies all count tasks are terminal, completes the cycle
  * count, and collects variance records for non-zero differences.
  *
  * @param cycleCountRepository
  *   repository for cycle count lookup and persistence
  * @param countTaskRepository
  *   repository for count task lookup
  */
class CountCompletionService(
    cycleCountRepository: CycleCountRepository,
    countTaskRepository: CountTaskRepository
):

  /** Attempts to complete a cycle count if all its count tasks are terminal.
    *
    * Steps: (1) validate cycle count is InProgress, (2) verify all count tasks are terminal
    * (Recorded or Cancelled), (3) complete the cycle count, (4) collect non-zero variances.
    *
    * @param cycleCountId
    *   the cycle count to complete
    * @param at
    *   instant of the completion
    * @return
    *   completion result or error
    */
  def tryComplete(
      cycleCountId: CycleCountId,
      at: Instant
  ): Either[CountCompletionError, CountCompletionResult] =
    cycleCountRepository.findById(cycleCountId) match
      case None => Left(CountCompletionError.CycleCountNotFound(cycleCountId))
      case Some(inProgress: CycleCount.InProgress) =>
        completeInProgress(inProgress, at)
      case Some(_) =>
        Left(CountCompletionError.CycleCountNotInProgress(cycleCountId))

  private def completeInProgress(
      inProgress: CycleCount.InProgress,
      at: Instant
  ): Either[CountCompletionError, CountCompletionResult] =
    val countTasks = countTaskRepository.findByCycleCountId(inProgress.id)

    val allTerminal = countTasks.forall {
      case _: CountTask.Recorded  => true
      case _: CountTask.Cancelled => true
      case _                      => false
    }

    if !allTerminal then return Left(CountCompletionError.OpenCountTasksRemaining(inProgress.id))

    val (completed, completedEvent) = inProgress.complete(at)
    cycleCountRepository.save(completed, completedEvent)

    val variances = countTasks
      .collect { case recorded: CountTask.Recorded =>
        recorded
      }
      .filter(_.variance != 0)
      .map { recorded =>
        CountVariance(
          countTaskId = recorded.id,
          skuId = recorded.skuId,
          locationId = recorded.locationId,
          expectedQuantity = recorded.expectedQuantity,
          actualQuantity = recorded.actualQuantity,
          variance = recorded.variance,
          countedBy = recorded.assignedTo
        )
      }

    Right(CountCompletionResult(completed, completedEvent, variances))
