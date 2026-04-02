package neon.infrastructure.actor

import neon.common.{LocationId, UserId}
import neon.infrastructure.serialization.CborSerializable
import neon.task.{Task, TaskEvent}

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria
}

import java.time.Instant

object TaskActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Task")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      planned: Task.Planned,
      event: TaskEvent.TaskCreated,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Allocate(
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
      at: Instant,
      replyTo: ActorRef[StatusReply[AllocateResponse]]
  ) extends Command

  case class Assign(
      userId: UserId,
      at: Instant,
      replyTo: ActorRef[StatusReply[AssignResponse]]
  ) extends Command

  case class Complete(
      actualQuantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[CompleteResponse]]
  ) extends Command

  case class Cancel(
      at: Instant,
      replyTo: ActorRef[StatusReply[CancelResponse]]
  ) extends Command

  case class GetState(replyTo: ActorRef[Option[Task]]) extends Command

  // --- Responses ---

  case class AllocateResponse(
      allocated: Task.Allocated,
      event: TaskEvent.TaskAllocated
  ) extends CborSerializable

  case class AssignResponse(
      assigned: Task.Assigned,
      event: TaskEvent.TaskAssigned
  ) extends CborSerializable

  case class CompleteResponse(
      completed: Task.Completed,
      event: TaskEvent.TaskCompleted
  ) extends CborSerializable

  case class CancelResponse(
      cancelled: Task.Cancelled,
      event: TaskEvent.TaskCancelled
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(task: Task) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, TaskEvent, State](
        persistenceId = PersistenceId(EntityKey.name, entityId),
        emptyState = EmptyState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
      .withTagger(_ => Set("task"))
      .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private val commandHandler: (State, Command) => ReplyEffect[TaskEvent, State] =
    (state, command) =>
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.ack())

        case (
              ActiveState(planned: Task.Planned),
              Allocate(srcLoc, dstLoc, at, replyTo)
            ) =>
          val (allocated, event) = planned.allocate(srcLoc, dstLoc, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(AllocateResponse(allocated, event)))

        case (
              ActiveState(allocated: Task.Allocated),
              Assign(userId, at, replyTo)
            ) =>
          val (assigned, event) = allocated.assign(userId, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(AssignResponse(assigned, event)))

        case (
              ActiveState(assigned: Task.Assigned),
              Complete(actualQty, at, replyTo)
            ) =>
          val (completed, event) = assigned.complete(actualQty, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CompleteResponse(completed, event)))

        case (ActiveState(planned: Task.Planned), Cancel(at, replyTo)) =>
          val (cancelled, event) = planned.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (
              ActiveState(allocated: Task.Allocated),
              Cancel(at, replyTo)
            ) =>
          val (cancelled, event) = allocated.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (
              ActiveState(assigned: Task.Assigned),
              Cancel(at, replyTo)
            ) =>
          val (cancelled, event) = assigned.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (_, GetState(replyTo)) =>
          val task = state match
            case EmptyState        => None
            case ActiveState(task) => Some(task)
          Effect.reply(replyTo)(task)

        case (_, cmd) =>
          rejectCommand(state, cmd)

  private def rejectCommand(
      state: State,
      cmd: Command
  ): ReplyEffect[TaskEvent, State] =
    val msg =
      s"Invalid command ${cmd.getClass.getSimpleName} in state ${state.getClass.getSimpleName}"
    cmd match
      case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Allocate => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Assign   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Complete => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Cancel   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: GetState => Effect.reply(c.replyTo)(None)

  // --- Event handler (state recovery) ---

  private val eventHandler: (State, TaskEvent) => State =
    (state, event) =>
      event match
        case e: TaskEvent.TaskCreated =>
          ActiveState(
            Task.Planned(
              e.taskId,
              e.taskType,
              e.skuId,
              e.packagingLevel,
              e.requestedQuantity,
              e.orderId,
              e.waveId,
              e.parentTaskId,
              e.handlingUnitId
            )
          )

        case e: TaskEvent.TaskAllocated =>
          state match
            case ActiveState(planned: Task.Planned) =>
              ActiveState(
                Task.Allocated(
                  planned.id,
                  planned.taskType,
                  planned.skuId,
                  planned.packagingLevel,
                  planned.requestedQuantity,
                  planned.orderId,
                  planned.waveId,
                  planned.parentTaskId,
                  planned.handlingUnitId,
                  e.sourceLocationId,
                  e.destinationLocationId
                )
              )
            case _ => state

        case e: TaskEvent.TaskAssigned =>
          state match
            case ActiveState(allocated: Task.Allocated) =>
              ActiveState(
                Task.Assigned(
                  allocated.id,
                  allocated.taskType,
                  allocated.skuId,
                  allocated.packagingLevel,
                  allocated.requestedQuantity,
                  allocated.orderId,
                  allocated.waveId,
                  allocated.parentTaskId,
                  allocated.handlingUnitId,
                  allocated.sourceLocationId,
                  allocated.destinationLocationId,
                  e.userId
                )
              )
            case _ => state

        case e: TaskEvent.TaskCompleted =>
          state match
            case ActiveState(assigned: Task.Assigned) =>
              ActiveState(
                Task.Completed(
                  assigned.id,
                  assigned.taskType,
                  assigned.skuId,
                  assigned.packagingLevel,
                  assigned.requestedQuantity,
                  e.actualQuantity,
                  assigned.orderId,
                  assigned.waveId,
                  assigned.parentTaskId,
                  assigned.handlingUnitId,
                  assigned.sourceLocationId,
                  assigned.destinationLocationId,
                  assigned.assignedTo
                )
              )
            case _ => state

        case e: TaskEvent.TaskCancelled =>
          state match
            case ActiveState(t: Task.Planned) =>
              ActiveState(
                Task.Cancelled(
                  t.id,
                  t.taskType,
                  t.skuId,
                  t.packagingLevel,
                  t.orderId,
                  t.waveId,
                  t.parentTaskId,
                  t.handlingUnitId,
                  e.sourceLocationId,
                  e.destinationLocationId,
                  e.assignedTo
                )
              )
            case ActiveState(t: Task.Allocated) =>
              ActiveState(
                Task.Cancelled(
                  t.id,
                  t.taskType,
                  t.skuId,
                  t.packagingLevel,
                  t.orderId,
                  t.waveId,
                  t.parentTaskId,
                  t.handlingUnitId,
                  e.sourceLocationId,
                  e.destinationLocationId,
                  e.assignedTo
                )
              )
            case ActiveState(t: Task.Assigned) =>
              ActiveState(
                Task.Cancelled(
                  t.id,
                  t.taskType,
                  t.skuId,
                  t.packagingLevel,
                  t.orderId,
                  t.waveId,
                  t.parentTaskId,
                  t.handlingUnitId,
                  e.sourceLocationId,
                  e.destinationLocationId,
                  e.assignedTo
                )
              )
            case _ => state
