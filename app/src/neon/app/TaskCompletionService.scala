package neon.app

import neon.common.TaskId
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.task.{Task, TaskEvent, TaskRepository}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}
import neon.wave.{Wave, WaveEvent, WaveRepository}

import java.time.Instant

/** Errors that can occur during task completion. */
sealed trait TaskCompletionError

object TaskCompletionError:
  /** The task was not found in the repository. */
  case class TaskNotFound(taskId: TaskId) extends TaskCompletionError

  /** The task is not in the [[Task.Assigned]] state required for completion. */
  case class TaskNotAssigned(taskId: TaskId) extends TaskCompletionError

  /** The actual quantity is negative. */
  case class InvalidActualQty(taskId: TaskId, actualQty: Int) extends TaskCompletionError

  /** The task's packaging level requires verification and none was provided. */
  case class VerificationRequired(taskId: TaskId) extends TaskCompletionError

/** The result of a successful task completion, containing all state transitions and events produced
  * by the cascade.
  *
  * @param completed
  *   the completed task
  * @param completedEvent
  *   the task completion event
  * @param shortpick
  *   replacement task if shortpicked, [[None]] otherwise
  * @param transportOrder
  *   routing transport order if the task has a handling unit
  * @param waveCompletion
  *   wave completion if all wave tasks are terminal
  * @param pickingCompletion
  *   consolidation group picking completion if all group tasks are terminal
  */
case class TaskCompletionResult(
    completed: Task.Completed,
    completedEvent: TaskEvent.TaskCompleted,
    shortpick: Option[(Task.Planned, TaskEvent.TaskCreated)],
    transportOrder: Option[(TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)],
    waveCompletion: Option[(Wave.Completed, WaveEvent.WaveCompleted)],
    pickingCompletion: Option[
      (ConsolidationGroup.Picked, ConsolidationGroupEvent.ConsolidationGroupPicked)
    ]
)

/** Orchestrates task completion with a 5-step cascade: complete the task, check for shortpick,
  * create routing transport order, detect wave completion, and detect picking completion for the
  * consolidation group.
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
  */
class TaskCompletionService(
    taskRepository: TaskRepository,
    waveRepository: WaveRepository,
    consolidationGroupRepository: ConsolidationGroupRepository,
    transportOrderRepository: TransportOrderRepository,
    verificationProfile: VerificationProfile
):
  /** Completes a task and runs the post-completion cascade.
    *
    * Steps: (1) complete the [[Task.Assigned]] task, (2) create shortpick replacement if partial,
    * (3) route handling unit via transport order, (4) check wave completion, (5) check picking
    * completion for the consolidation group.
    *
    * @param taskId
    *   the task to complete
    * @param actualQty
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
      actualQty: Int,
      verified: Boolean,
      at: Instant
  ): Either[TaskCompletionError, TaskCompletionResult] =
    if actualQty < 0 then return Left(TaskCompletionError.InvalidActualQty(taskId, actualQty))

    taskRepository.findById(taskId) match
      case None                          => Left(TaskCompletionError.TaskNotFound(taskId))
      case Some(assigned: Task.Assigned) =>
        if verificationProfile.requiresVerification(assigned.packagingLevel) && !verified
        then Left(TaskCompletionError.VerificationRequired(taskId))
        else completeAssigned(assigned, actualQty, at)
      case Some(_) => Left(TaskCompletionError.TaskNotAssigned(taskId))

  /** Runs the full cascade for a validated [[Task.Assigned]] task. */
  private def completeAssigned(
      assigned: Task.Assigned,
      actualQty: Int,
      at: Instant
  ): Either[TaskCompletionError, TaskCompletionResult] =
    val (completed, completedEvent) = assigned.complete(actualQty, at)
    taskRepository.save(completed, completedEvent)

    val shortpick = ShortpickPolicy(completed, at)
    shortpick.foreach { (replacement, event) => taskRepository.save(replacement, event) }

    val routing = RoutingPolicy(completedEvent, at)
    routing.foreach { (pending, event) => transportOrderRepository.save(pending, event) }

    val (waveCompletion, pickingCompletion) = completed.waveId match
      case None         => (None, None)
      case Some(waveId) =>
        val waveTasks = taskRepository.findByWaveId(waveId)

        val completedWave = waveRepository
          .findById(waveId)
          .collect { case released: Wave.Released =>
            WaveCompletionPolicy(waveTasks, released, at)
          }
          .flatten
        completedWave.foreach { (wave, event) => waveRepository.save(wave, event) }

        val pickedConsolidationGroup = consolidationGroupRepository
          .findByWaveId(waveId)
          .collectFirst {
            case consolidationGroup: ConsolidationGroup.Created
                if consolidationGroup.orderIds.contains(completed.orderId) =>
              consolidationGroup
          }
          .flatMap { consolidationGroup =>
            val consolidationGroupOrderIds = consolidationGroup.orderIds.toSet
            val consolidationGroupTasks =
              waveTasks.filter(t => consolidationGroupOrderIds.contains(t.orderId))
            PickingCompletionPolicy(consolidationGroupTasks, consolidationGroup, at)
          }
        pickedConsolidationGroup.foreach { (consolidationGroup, event) =>
          consolidationGroupRepository.save(consolidationGroup, event)
        }

        (completedWave, pickedConsolidationGroup)

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
