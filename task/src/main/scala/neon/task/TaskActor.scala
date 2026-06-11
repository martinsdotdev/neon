package neon.task

import neon.common.entity.EventSourcedEntity
import neon.common.serialization.CborSerializable
import neon.common.{LocationId, UserId}
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.typed.scaladsl.{Effect, ReplyEffect}

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
    EventSourcedEntity.behavior[Command, TaskEvent, State](
      entityKey = EntityKey,
      entityId = entityId,
      emptyState = EmptyState,
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )

  // --- Command handler ---

  private def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => ReplyEffect[TaskEvent, State] =
    (state, command) =>
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.ack())

        case (
              ActiveState(planned: Task.Planned),
              Allocate(sourceLocationId, destinationLocationId, at, replyTo)
            ) =>
          val (allocated, event) = planned.allocate(
            sourceLocationId = sourceLocationId,
            destinationLocationId = destinationLocationId,
            at = at
          )
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
              Complete(actualQuantity, at, replyTo)
            ) =>
          val (completed, event) = assigned.complete(actualQuantity, at)
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
          rejectCommand(context, state, cmd)

  private def rejectCommand(
      context: ActorContext[Command],
      state: State,
      cmd: Command
  ): ReplyEffect[TaskEvent, State] =
    val msg = EventSourcedEntity.invalidCommandMessage(state, cmd)
    context.log.warn(msg)
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
              id = e.taskId,
              taskType = e.taskType,
              skuId = e.skuId,
              packagingLevel = e.packagingLevel,
              requestedQuantity = e.requestedQuantity,
              orderId = e.orderId,
              waveId = e.waveId,
              parentTaskId = e.parentTaskId,
              handlingUnitId = e.handlingUnitId,
              stockPositionId = e.stockPositionId
            )
          )

        case e: TaskEvent.TaskAllocated =>
          state match
            case ActiveState(planned: Task.Planned) =>
              ActiveState(
                Task.Allocated(
                  id = planned.id,
                  taskType = planned.taskType,
                  skuId = planned.skuId,
                  packagingLevel = planned.packagingLevel,
                  requestedQuantity = planned.requestedQuantity,
                  orderId = planned.orderId,
                  waveId = planned.waveId,
                  parentTaskId = planned.parentTaskId,
                  handlingUnitId = planned.handlingUnitId,
                  stockPositionId = planned.stockPositionId,
                  sourceLocationId = e.sourceLocationId,
                  destinationLocationId = e.destinationLocationId
                )
              )
            case _ => state

        case e: TaskEvent.TaskAssigned =>
          state match
            case ActiveState(allocated: Task.Allocated) =>
              ActiveState(
                Task.Assigned(
                  id = allocated.id,
                  taskType = allocated.taskType,
                  skuId = allocated.skuId,
                  packagingLevel = allocated.packagingLevel,
                  requestedQuantity = allocated.requestedQuantity,
                  orderId = allocated.orderId,
                  waveId = allocated.waveId,
                  parentTaskId = allocated.parentTaskId,
                  handlingUnitId = allocated.handlingUnitId,
                  stockPositionId = allocated.stockPositionId,
                  sourceLocationId = allocated.sourceLocationId,
                  destinationLocationId = allocated.destinationLocationId,
                  assignedTo = e.userId
                )
              )
            case _ => state

        case e: TaskEvent.TaskCompleted =>
          state match
            case ActiveState(assigned: Task.Assigned) =>
              ActiveState(
                Task.Completed(
                  id = assigned.id,
                  taskType = assigned.taskType,
                  skuId = assigned.skuId,
                  packagingLevel = assigned.packagingLevel,
                  requestedQuantity = assigned.requestedQuantity,
                  actualQuantity = e.actualQuantity,
                  orderId = assigned.orderId,
                  waveId = assigned.waveId,
                  parentTaskId = assigned.parentTaskId,
                  handlingUnitId = assigned.handlingUnitId,
                  stockPositionId = assigned.stockPositionId,
                  sourceLocationId = assigned.sourceLocationId,
                  destinationLocationId = assigned.destinationLocationId,
                  assignedTo = assigned.assignedTo
                )
              )
            case _ => state

        case e: TaskEvent.TaskCancelled =>
          state match
            case ActiveState(t: Task.Planned) =>
              ActiveState(
                Task.Cancelled(
                  id = t.id,
                  taskType = t.taskType,
                  skuId = t.skuId,
                  packagingLevel = t.packagingLevel,
                  orderId = t.orderId,
                  waveId = t.waveId,
                  parentTaskId = t.parentTaskId,
                  handlingUnitId = t.handlingUnitId,
                  stockPositionId = t.stockPositionId,
                  sourceLocationId = e.sourceLocationId,
                  destinationLocationId = e.destinationLocationId,
                  assignedTo = e.assignedTo
                )
              )
            case ActiveState(t: Task.Allocated) =>
              ActiveState(
                Task.Cancelled(
                  id = t.id,
                  taskType = t.taskType,
                  skuId = t.skuId,
                  packagingLevel = t.packagingLevel,
                  orderId = t.orderId,
                  waveId = t.waveId,
                  parentTaskId = t.parentTaskId,
                  handlingUnitId = t.handlingUnitId,
                  stockPositionId = t.stockPositionId,
                  sourceLocationId = e.sourceLocationId,
                  destinationLocationId = e.destinationLocationId,
                  assignedTo = e.assignedTo
                )
              )
            case ActiveState(t: Task.Assigned) =>
              ActiveState(
                Task.Cancelled(
                  id = t.id,
                  taskType = t.taskType,
                  skuId = t.skuId,
                  packagingLevel = t.packagingLevel,
                  orderId = t.orderId,
                  waveId = t.waveId,
                  parentTaskId = t.parentTaskId,
                  handlingUnitId = t.handlingUnitId,
                  stockPositionId = t.stockPositionId,
                  sourceLocationId = e.sourceLocationId,
                  destinationLocationId = e.destinationLocationId,
                  assignedTo = e.assignedTo
                )
              )
            case _ => state
