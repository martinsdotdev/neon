package neon.cyclecount

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

object CycleCountActor:

  // --- Entity key for cluster sharding ---

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("CycleCount")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      newCycleCount: CycleCount.New,
      event: CycleCountEvent.CycleCountCreated,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Start(
      at: Instant,
      replyTo: ActorRef[StatusReply[StartResponse]]
  ) extends Command

  case class Complete(
      at: Instant,
      replyTo: ActorRef[StatusReply[CompleteResponse]]
  ) extends Command

  case class Cancel(
      at: Instant,
      replyTo: ActorRef[StatusReply[CancelResponse]]
  ) extends Command

  case class GetState(replyTo: ActorRef[Option[CycleCount]]) extends Command

  // --- Responses ---

  case class StartResponse(
      inProgress: CycleCount.InProgress,
      event: CycleCountEvent.CycleCountStarted
  ) extends CborSerializable

  case class CompleteResponse(
      completed: CycleCount.Completed,
      event: CycleCountEvent.CycleCountCompleted
  ) extends CborSerializable

  case class CancelResponse(
      cancelled: CycleCount.Cancelled,
      event: CycleCountEvent.CycleCountCancelled
  ) extends CborSerializable

  // --- Actor state ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(cycleCount: CycleCount) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map("entityType" -> "CycleCount", "entityId" -> entityId)
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[Command, CycleCountEvent, State](
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
  ): (State, Command) => ReplyEffect[CycleCountEvent, State] =
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

        case (ActiveState(newCount: CycleCount.New), Start(at, replyTo)) =>
          val (inProgress, event) = newCount.start(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(StartResponse(inProgress, event)))

        case (ActiveState(inProgress: CycleCount.InProgress), Complete(at, replyTo)) =>
          val (completed, event) = inProgress.complete(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CompleteResponse(completed, event)))

        case (ActiveState(newCount: CycleCount.New), Cancel(at, replyTo)) =>
          val (cancelled, event) = newCount.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (ActiveState(inProgress: CycleCount.InProgress), Cancel(at, replyTo)) =>
          val (cancelled, event) = inProgress.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (_, GetState(replyTo)) =>
          val cycleCount = state match
            case EmptyState              => None
            case ActiveState(cycleCount) => Some(cycleCount)
          Effect.reply(replyTo)(cycleCount)

        case (_, cmd) =>
          rejectCommand(context, state, cmd)

  private def rejectCommand(
      context: ActorContext[Command],
      state: State,
      cmd: Command
  ): ReplyEffect[CycleCountEvent, State] =
    val msg =
      s"Invalid command ${cmd.getClass.getSimpleName} " +
        s"in state ${state.getClass.getSimpleName}"
    context.log.warn(msg)
    cmd match
      case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Start    => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Complete => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: Cancel   => Effect.reply(c.replyTo)(StatusReply.error(msg))
      case c: GetState => Effect.reply(c.replyTo)(None)

  // --- Event handler (state recovery) ---

  private val eventHandler: (State, CycleCountEvent) => State =
    (state, event) =>
      event match
        case e: CycleCountEvent.CycleCountCreated =>
          ActiveState(
            CycleCount.New(
              e.cycleCountId,
              e.warehouseAreaId,
              e.skuIds,
              e.countType,
              e.countMethod
            )
          )
        case e: CycleCountEvent.CycleCountStarted =>
          state match
            case ActiveState(c) =>
              ActiveState(
                CycleCount.InProgress(
                  c.id,
                  c.warehouseAreaId,
                  c.skuIds,
                  e.countType,
                  e.countMethod
                )
              )
            case _ => state
        case e: CycleCountEvent.CycleCountCompleted =>
          state match
            case ActiveState(c) =>
              ActiveState(
                CycleCount.Completed(c.id, c.warehouseAreaId, c.skuIds, c.countType, c.countMethod)
              )
            case _ => state
        case e: CycleCountEvent.CycleCountCancelled =>
          state match
            case ActiveState(c) =>
              ActiveState(
                CycleCount.Cancelled(c.id, c.warehouseAreaId, c.skuIds, c.countType, c.countMethod)
              )
            case _ => state
