package neon.core

import neon.common.TaskId
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent,
  ConsolidationGroupRepository
}
import neon.stockposition.{StockPosition, StockPositionEvent, StockPositionRepository}
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
  case class InvalidActualQuantity(taskId: TaskId, actualQuantity: Int) extends TaskCompletionError

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
  * @param stockConsumption
  *   stock position update from consuming or deallocating allocated stock
  */
case class TaskCompletionResult(
    completed: Task.Completed,
    completedEvent: TaskEvent.TaskCompleted,
    shortpick: Option[(Task.Planned, TaskEvent.TaskCreated)],
    transportOrder: Option[(TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)],
    waveCompletion: Option[(Wave.Completed, WaveEvent.WaveCompleted)],
    pickingCompletion: Option[
      (ConsolidationGroup.Picked, ConsolidationGroupEvent.ConsolidationGroupPicked)
    ],
    stockConsumption: Option[(StockPosition, StockPositionEvent)] = None
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
    verificationProfile: VerificationProfile,
    stockPositionRepository: Option[StockPositionRepository] = None
):
  /** Completes a task and runs the post-completion cascade.
    *
    * Steps: (1) complete the [[Task.Assigned]] task, (2) create shortpick replacement if partial,
    * (3) route handling unit via transport order, (4) check wave completion, (5) check picking
    * completion for the consolidation group.
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
    if actualQuantity < 0 then
      return Left(TaskCompletionError.InvalidActualQuantity(taskId, actualQuantity))

    taskRepository.findById(taskId) match
      case None                          => Left(TaskCompletionError.TaskNotFound(taskId))
      case Some(assigned: Task.Assigned) =>
        if verificationProfile.requiresVerification(assigned.packagingLevel) && !verified
        then Left(TaskCompletionError.VerificationRequired(taskId))
        else completeAssigned(assigned, actualQuantity, at)
      case Some(_) => Left(TaskCompletionError.TaskNotAssigned(taskId))

  /** Runs the full cascade for a validated [[Task.Assigned]] task. */
  private def completeAssigned(
      assigned: Task.Assigned,
      actualQuantity: Int,
      at: Instant
  ): Either[TaskCompletionError, TaskCompletionResult] =
    val (completed, completedEvent) = assigned.complete(actualQuantity, at)
    taskRepository.save(completed, completedEvent)

    val stockConsumption = consumeOrDeallocateStock(completed, at)

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
        pickingCompletion = pickingCompletion,
        stockConsumption = stockConsumption
      )
    )

  /** Consumes allocated stock for the actual quantity picked and deallocates any remainder back to
    * available. Skipped when no stock repository is provided or the task has no stock position.
    */
  private def consumeOrDeallocateStock(
      completed: Task.Completed,
      at: Instant
  ): Option[(StockPosition, StockPositionEvent)] =
    (stockPositionRepository, completed.stockPositionId) match
      case (Some(spRepo), Some(spId)) =>
        spRepo.findById(spId).flatMap { sp =>
          val remainder = completed.requestedQuantity - completed.actualQuantity
          if completed.actualQuantity > 0 && remainder > 0 then
            // Partial pick: consume actual, then deallocate remainder
            val (afterConsume, consumeEvent) =
              sp.consumeAllocated(completed.actualQuantity, at)
            spRepo.save(afterConsume, consumeEvent)
            val (afterDeallocate, deallocateEvent) =
              afterConsume.deallocate(remainder, at)
            spRepo.save(afterDeallocate, deallocateEvent)
            Some((afterDeallocate, deallocateEvent))
          else if completed.actualQuantity > 0 then
            // Full pick: consume all
            val (updated, event) =
              sp.consumeAllocated(completed.actualQuantity, at)
            spRepo.save(updated, event)
            Some((updated, event))
          else if completed.requestedQuantity > 0 then
            // Zero pick (full shortpick): deallocate all back to available
            val (updated, event) =
              sp.deallocate(completed.requestedQuantity, at)
            spRepo.save(updated, event)
            Some((updated, event))
          else None
        }
      case _ => None
