package neon.app

import neon.common.TaskId
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.task.{Task, TaskEvent, TaskRepository}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}
import neon.wave.{Wave, WaveEvent, WaveRepository}

import java.time.Instant

sealed trait TaskCompletionError

object TaskCompletionError:
  case class TaskNotFound(taskId: TaskId) extends TaskCompletionError
  case class TaskNotAssigned(taskId: TaskId) extends TaskCompletionError
  case class InvalidActualQty(taskId: TaskId, actualQty: Int) extends TaskCompletionError

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

class TaskCompletionService(
    taskRepository: TaskRepository,
    waveRepository: WaveRepository,
    consolidationGroupRepository: ConsolidationGroupRepository,
    transportOrderRepository: TransportOrderRepository
):
  def complete(
      taskId: TaskId,
      actualQty: Int,
      at: Instant
  ): Either[TaskCompletionError, TaskCompletionResult] =
    if actualQty < 0 then return Left(TaskCompletionError.InvalidActualQty(taskId, actualQty))

    taskRepository.findById(taskId) match
      case None                          => Left(TaskCompletionError.TaskNotFound(taskId))
      case Some(assigned: Task.Assigned) => completeAssigned(assigned, actualQty, at)
      case Some(_)                       => Left(TaskCompletionError.TaskNotAssigned(taskId))

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
