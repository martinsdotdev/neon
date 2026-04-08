package neon.wave

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

object WaveActor:

  // --- Entity key for cluster sharding ---

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Wave")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      planned: Wave.Planned,
      event: WaveEvent.WaveReleased,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Release(
      at: Instant,
      replyTo: ActorRef[StatusReply[ReleaseResponse]]
  ) extends Command

  case class Complete(
      at: Instant,
      replyTo: ActorRef[StatusReply[CompleteResponse]]
  ) extends Command

  case class Cancel(
      at: Instant,
      replyTo: ActorRef[StatusReply[CancelResponse]]
  ) extends Command

  case class GetState(replyTo: ActorRef[Option[Wave]]) extends Command

  // --- Responses ---

  case class ReleaseResponse(
      released: Wave.Released,
      event: WaveEvent.WaveReleased
  ) extends CborSerializable

  case class CompleteResponse(
      completed: Wave.Completed,
      event: WaveEvent.WaveCompleted
  ) extends CborSerializable

  case class CancelResponse(
      cancelled: Wave.Cancelled,
      event: WaveEvent.WaveCancelled
  ) extends CborSerializable

  // --- Actor state ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(wave: Wave) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map("entityType" -> "Wave", "entityId" -> entityId)
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[Command, WaveEvent, State](
            persistenceId = PersistenceId(EntityKey.name, entityId),
            emptyState = EmptyState,
            commandHandler = commandHandler(context),
            eventHandler = eventHandler
          )
          .withTagger(_ => Set("wave"))
          .withRetention(
            RetentionCriteria.snapshotEvery(100, 2)
          )

  // --- Command handler ---

  private def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => ReplyEffect[WaveEvent, State] =
    (state, command) =>
      context.log.debug(
        "Received {} in state {}",
        command.getClass.getSimpleName,
        state.getClass.getSimpleName
      )
      (state, command) match

        case (EmptyState, Create(planned, event, replyTo)) =>
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.ack())

        case (ActiveState(planned: Wave.Planned), Release(at, replyTo)) =>
          val (released, event) = planned.release(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(ReleaseResponse(released, event)))

        case (
              ActiveState(released: Wave.Released),
              Complete(at, replyTo)
            ) =>
          val (completed, event) = released.complete(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CompleteResponse(completed, event)))

        case (ActiveState(planned: Wave.Planned), Cancel(at, replyTo)) =>
          val (cancelled, event) = planned.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (
              ActiveState(released: Wave.Released),
              Cancel(at, replyTo)
            ) =>
          val (cancelled, event) = released.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (_, GetState(replyTo)) =>
          val wave = state match
            case EmptyState        => None
            case ActiveState(wave) => Some(wave)
          Effect.reply(replyTo)(wave)

        case (_, cmd) =>
          val msg =
            s"Invalid command ${cmd.getClass.getSimpleName} " +
              s"in state ${state.getClass.getSimpleName}"
          context.log.warn(msg)
          cmd match
            case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Release  => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Complete => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Cancel   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState => Effect.reply(c.replyTo)(None)

  // --- Event handler (state recovery) ---

  private val eventHandler: (State, WaveEvent) => State =
    (state, event) =>
      event match
        case e: WaveEvent.WaveReleased =>
          ActiveState(Wave.Released(e.waveId, e.orderGrouping, e.orderIds))
        case e: WaveEvent.WaveCompleted =>
          state match
            case ActiveState(w) =>
              ActiveState(Wave.Completed(w.id, w.orderGrouping))
            case _ => state
        case e: WaveEvent.WaveCancelled =>
          state match
            case ActiveState(w) =>
              ActiveState(Wave.Cancelled(w.id, w.orderGrouping))
            case _ => state
