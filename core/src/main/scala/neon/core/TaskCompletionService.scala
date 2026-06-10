package neon.core

import neon.common.TaskId
import neon.consolidationgroup.ConsolidationGroupRepository
import neon.core.TaskCompletionCascade.CascadeState
import neon.stockposition.StockPositionRepository
import neon.task.{Task, TaskRepository}
import neon.transportorder.TransportOrderRepository
import neon.wave.WaveRepository

import java.time.Instant

/** Synchronous shell around [[TaskCompletionCascade]]: loads the cascade state, delegates every
  * decision to the pure module, and persists the outcome.
  *
  * @param taskRepository
  *   repository for task lookup and persistence
  * @param waveRepository
  *   repository for wave lookup and persistence
  * @param consolidationGroupRepository
  *   repository for consolidation group lookup and persistence
  * @param transportOrderRepository
  *   repository for transport order persistence
  * @param verificationProfile
  *   defines which packaging levels require verification scanning
  * @param stockPositionRepository
  *   optional stock repository; when absent, the cascade decides without stock writes
  */
class TaskCompletionService(
    taskRepository: TaskRepository,
    waveRepository: WaveRepository,
    consolidationGroupRepository: ConsolidationGroupRepository,
    transportOrderRepository: TransportOrderRepository,
    verificationProfile: VerificationProfile,
    stockPositionRepository: Option[StockPositionRepository] = None
):
  /** Completes a task and runs the post-completion cascade.
    *
    * @param taskId
    *   the task to complete
    * @param actualQuantity
    *   the actual quantity picked or handled
    * @param verified
    *   whether the pick was verification-scanned
    * @param at
    *   instant of the completion
    * @return
    *   completion result or error
    */
  def complete(
      taskId: TaskId,
      actualQuantity: Int,
      verified: Boolean,
      at: Instant
  ): Either[TaskCompletionError, TaskCompletionResult] =
    TaskCompletionCascade
      .validate(
        taskId = taskId,
        task = taskRepository.findById(taskId),
        actualQuantity = actualQuantity,
        verified = verified,
        verificationProfile = verificationProfile
      )
      .map { assigned =>
        val outcome = TaskCompletionCascade.decide(
          assigned = assigned,
          actualQuantity = actualQuantity,
          at = at,
          state = loadCascadeState(assigned)
        )
        persist(outcome)
        outcome.result
      }

  /** Loads everything the cascade needs. All loads happen before the decision. */
  private def loadCascadeState(assigned: Task.Assigned): CascadeState =
    val stockPosition =
      for
        repository <- stockPositionRepository
        stockPositionId <- assigned.stockPositionId
        position <- repository.findById(stockPositionId)
      yield position
    assigned.waveId match
      case None         => CascadeState.empty.copy(stockPosition = stockPosition)
      case Some(waveId) =>
        CascadeState(
          stockPosition = stockPosition,
          wave = waveRepository.findById(waveId),
          waveTasks = taskRepository.findByWaveId(waveId),
          consolidationGroups = consolidationGroupRepository.findByWaveId(waveId)
        )

  /** Persists the outcome in cascade order: task, stock writes, shortpick replacement, transport
    * order, wave completion, picking completion.
    */
  private def persist(outcome: TaskCompletionCascade.Outcome): Unit =
    taskRepository.save(outcome.result.completed, outcome.result.completedEvent)
    stockPositionRepository.foreach { repository =>
      outcome.stockWrites.foreach { (position, event) => repository.save(position, event) }
    }
    outcome.result.shortpick.foreach { (replacement, event) =>
      taskRepository.save(replacement, event)
    }
    outcome.result.transportOrder.foreach { (pending, event) =>
      transportOrderRepository.save(pending, event)
    }
    outcome.result.waveCompletion.foreach { (wave, event) => waveRepository.save(wave, event) }
    outcome.result.pickingCompletion.foreach { (group, event) =>
      consolidationGroupRepository.save(group, event)
    }
