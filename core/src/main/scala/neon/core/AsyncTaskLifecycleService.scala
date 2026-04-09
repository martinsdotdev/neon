package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.{LocationId, TaskId, UserId}
import neon.task.{AsyncTaskRepository, Task, TaskEvent}
import neon.user.AsyncUserRepository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait TaskLifecycleError
object TaskLifecycleError:
  case class TaskNotFound(taskId: TaskId) extends TaskLifecycleError
  case class TaskInWrongState(taskId: TaskId) extends TaskLifecycleError
  case class TaskAlreadyTerminal(taskId: TaskId) extends TaskLifecycleError
  case class UserNotFound(userId: UserId) extends TaskLifecycleError
  case class UserNotActive(userId: UserId) extends TaskLifecycleError

case class TaskAllocateResult(
    allocated: Task.Allocated,
    event: TaskEvent.TaskAllocated
)
case class TaskAssignResult(
    assigned: Task.Assigned,
    event: TaskEvent.TaskAssigned
)
case class TaskCancelResult(
    cancelled: Task.Cancelled,
    event: TaskEvent.TaskCancelled
)

class AsyncTaskLifecycleService(
    taskRepository: AsyncTaskRepository,
    userRepository: AsyncUserRepository
)(using ExecutionContext)
    extends LazyLogging:

  def allocate(
      taskId: TaskId,
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
      at: Instant
  ): Future[Either[TaskLifecycleError, TaskAllocateResult]] =
    taskRepository
      .findById(taskId)
      .flatMap:
        case None => Future.successful(Left(TaskLifecycleError.TaskNotFound(taskId)))
        case Some(planned: Task.Planned) =>
          val (allocated, event) = planned.allocate(sourceLocationId, destinationLocationId, at)
          taskRepository
            .save(allocated, event)
            .map(_ => Right(TaskAllocateResult(allocated, event)))
        case Some(_) => Future.successful(Left(TaskLifecycleError.TaskInWrongState(taskId)))

  def assign(
      taskId: TaskId,
      userId: UserId,
      at: Instant
  ): Future[Either[TaskLifecycleError, TaskAssignResult]] =
    taskRepository
      .findById(taskId)
      .flatMap:
        case None => Future.successful(Left(TaskLifecycleError.TaskNotFound(taskId)))
        case Some(allocated: Task.Allocated) =>
          userRepository
            .findById(userId)
            .flatMap:
              case None => Future.successful(Left(TaskLifecycleError.UserNotFound(userId)))
              case Some(user) if !user.active =>
                Future.successful(Left(TaskLifecycleError.UserNotActive(userId)))
              case Some(_) =>
                val (assigned, event) = allocated.assign(userId, at)
                taskRepository
                  .save(assigned, event)
                  .map(_ => Right(TaskAssignResult(assigned, event)))
        case Some(_) => Future.successful(Left(TaskLifecycleError.TaskInWrongState(taskId)))

  def cancel(
      taskId: TaskId,
      at: Instant
  ): Future[Either[TaskLifecycleError, TaskCancelResult]] =
    taskRepository
      .findById(taskId)
      .flatMap:
        case None => Future.successful(Left(TaskLifecycleError.TaskNotFound(taskId)))
        case Some(planned: Task.Planned) =>
          val (cancelled, event) = planned.cancel(at)
          taskRepository.save(cancelled, event).map(_ => Right(TaskCancelResult(cancelled, event)))
        case Some(allocated: Task.Allocated) =>
          val (cancelled, event) = allocated.cancel(at)
          taskRepository.save(cancelled, event).map(_ => Right(TaskCancelResult(cancelled, event)))
        case Some(assigned: Task.Assigned) =>
          val (cancelled, event) = assigned.cancel(at)
          taskRepository.save(cancelled, event).map(_ => Right(TaskCancelResult(cancelled, event)))
        case Some(_) => Future.successful(Left(TaskLifecycleError.TaskAlreadyTerminal(taskId)))
