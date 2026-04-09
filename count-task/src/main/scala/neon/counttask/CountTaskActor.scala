package neon.counttask

import neon.common.UserId
import neon.common.serialization.CborSerializable
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
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

object CountTaskActor:

  // --- Entity key for cluster sharding ---

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("CountTask")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      pending: CountTask.Pending,
      event: CountTaskEvent.CountTaskCreated,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Assign(
      userId: UserId,
      at: Instant,
      replyTo: ActorRef[StatusReply[AssignResponse]]
  ) extends Command

  case class Record(
      actualQuantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[RecordResponse]]
  ) extends Command

  case class Cancel(
      at: Instant,
      replyTo: ActorRef[StatusReply[CancelResponse]]
  ) extends Command

  case class GetState(replyTo: ActorRef[Option[CountTask]]) extends Command

  // --- Responses ---

  case class AssignResponse(
      assigned: CountTask.Assigned,
      event: CountTaskEvent.CountTaskAssigned
  ) extends CborSerializable

  case class RecordResponse(
      recorded: CountTask.Recorded,
      event: CountTaskEvent.CountTaskRecorded
  ) extends CborSerializable

  case class CancelResponse(
      cancelled: CountTask.Cancelled,
      event: CountTaskEvent.CountTaskCancelled
  ) extends CborSerializable

  // --- Actor state ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(countTask: CountTask) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map("entityType" -> "CountTask", "entityId" -> entityId)
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[Command, CountTaskEvent, State](
            persistenceId = PersistenceId(EntityKey.name, entityId),
            emptyState = EmptyState,
            commandHandler = commandHandler(context),
            eventHandler = eventHandler
          )
          .withRetention(
            RetentionCriteria.snapshotEvery(100, 2)
          )

  // --- Command handler ---

  private def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => ReplyEffect[CountTaskEvent, State] =
    (state, command) =>
      context.log.debug(
        "Received {} in state {}",
        command.getClass.getSimpleName,
        state.getClass.getSimpleName
      )
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.ack())

        case (ActiveState(pending: CountTask.Pending), Assign(userId, at, replyTo)) =>
          val (assigned, event) = pending.assign(userId, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(AssignResponse(assigned, event)))

        case (ActiveState(assigned: CountTask.Assigned), Record(actualQuantity, at, replyTo)) =>
          val (recorded, event) = assigned.record(actualQuantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(RecordResponse(recorded, event)))

        case (ActiveState(pending: CountTask.Pending), Cancel(at, replyTo)) =>
          val (cancelled, event) = pending.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (ActiveState(assigned: CountTask.Assigned), Cancel(at, replyTo)) =>
          val (cancelled, event) = assigned.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (_, GetState(replyTo)) =>
          val countTask = state match
            case EmptyState             => None
            case ActiveState(countTask) => Some(countTask)
          Effect.reply(replyTo)(countTask)

        case (_, cmd) =>
          rejectCommand(context, state, cmd)

  private def rejectCommand(
      context: ActorContext[Command],
      state: State,
      cmd: Command
  ): ReplyEffect[CountTaskEvent, State] =
    val msg =
      s"Invalid command ${cmd.getClass.getSimpleName} " +
        s"in state ${state.getClass.getSimpleName}"
    context.log.warn(msg)
    cmd match
      case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Assign   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Record   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Cancel   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: GetState => Effect.reply(c.replyTo)(None)

  // --- Event handler (state recovery) ---

  private val eventHandler: (State, CountTaskEvent) => State =
    (state, event) =>
      event match
        case e: CountTaskEvent.CountTaskCreated =>
          ActiveState(
            CountTask.Pending(
              e.countTaskId,
              e.cycleCountId,
              e.skuId,
              e.locationId,
              e.expectedQuantity
            )
          )
        case e: CountTaskEvent.CountTaskAssigned =>
          state match
            case ActiveState(pending: CountTask.Pending) =>
              ActiveState(
                CountTask.Assigned(
                  pending.id,
                  pending.cycleCountId,
                  pending.skuId,
                  pending.locationId,
                  pending.expectedQuantity,
                  e.userId
                )
              )
            case _ => state
        case e: CountTaskEvent.CountTaskRecorded =>
          state match
            case ActiveState(assigned: CountTask.Assigned) =>
              ActiveState(
                CountTask.Recorded(
                  assigned.id,
                  assigned.cycleCountId,
                  assigned.skuId,
                  assigned.locationId,
                  assigned.expectedQuantity,
                  assigned.assignedTo,
                  e.actualQuantity,
                  e.variance
                )
              )
            case _ => state
        case e: CountTaskEvent.CountTaskCancelled =>
          state match
            case ActiveState(pending: CountTask.Pending) =>
              ActiveState(
                CountTask.Cancelled(
                  pending.id,
                  pending.cycleCountId,
                  pending.skuId,
                  pending.locationId,
                  pending.expectedQuantity,
                  None
                )
              )
            case ActiveState(assigned: CountTask.Assigned) =>
              ActiveState(
                CountTask.Cancelled(
                  assigned.id,
                  assigned.cycleCountId,
                  assigned.skuId,
                  assigned.locationId,
                  assigned.expectedQuantity,
                  Some(assigned.assignedTo)
                )
              )
            case _ => state
