package neon.transportorder

import neon.common.serialization.CborSerializable
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

object TransportOrderActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("TransportOrder")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      pending: TransportOrder.Pending,
      event: TransportOrderEvent.TransportOrderCreated,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Confirm(
      at: Instant,
      replyTo: ActorRef[StatusReply[ConfirmResponse]]
  ) extends Command

  case class Cancel(
      at: Instant,
      replyTo: ActorRef[StatusReply[CancelResponse]]
  ) extends Command

  case class GetState(
      replyTo: ActorRef[Option[TransportOrder]]
  ) extends Command

  // --- Responses ---

  case class ConfirmResponse(
      confirmed: TransportOrder.Confirmed,
      event: TransportOrderEvent.TransportOrderConfirmed
  ) extends CborSerializable

  case class CancelResponse(
      cancelled: TransportOrder.Cancelled,
      event: TransportOrderEvent.TransportOrderCancelled
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(order: TransportOrder) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, TransportOrderEvent, State](
        persistenceId = PersistenceId(EntityKey.name, entityId),
        emptyState = EmptyState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
      .withTagger(_ => Set("transport-order"))
      .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private val commandHandler: (State, Command) => ReplyEffect[TransportOrderEvent, State] =
    (state, command) =>
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect.persist(event).thenReply(replyTo)(_ => StatusReply.ack())

        case (
              ActiveState(p: TransportOrder.Pending),
              Confirm(at, replyTo)
            ) =>
          val (confirmed, event) = p.confirm(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(ConfirmResponse(confirmed, event)))

        case (
              ActiveState(p: TransportOrder.Pending),
              Cancel(at, replyTo)
            ) =>
          val (cancelled, event) = p.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (_, GetState(replyTo)) =>
          val order = state match
            case EmptyState         => None
            case ActiveState(order) => Some(order)
          Effect.reply(replyTo)(order)

        case (_, cmd) =>
          val msg = s"Invalid command ${cmd.getClass.getSimpleName} " +
            s"in state ${state.getClass.getSimpleName}"
          cmd match
            case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Confirm  => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Cancel   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState => Effect.reply(c.replyTo)(None)

  // --- Event handler ---

  private val eventHandler: (State, TransportOrderEvent) => State =
    (state, event) =>
      event match
        case e: TransportOrderEvent.TransportOrderCreated =>
          ActiveState(
            TransportOrder.Pending(
              e.transportOrderId,
              e.handlingUnitId,
              e.destination
            )
          )
        case e: TransportOrderEvent.TransportOrderConfirmed =>
          state match
            case ActiveState(p: TransportOrder.Pending) =>
              ActiveState(
                TransportOrder.Confirmed(
                  p.id,
                  p.handlingUnitId,
                  p.destination
                )
              )
            case _ => state
        case e: TransportOrderEvent.TransportOrderCancelled =>
          state match
            case ActiveState(p: TransportOrder.Pending) =>
              ActiveState(
                TransportOrder.Cancelled(
                  p.id,
                  p.handlingUnitId,
                  p.destination
                )
              )
            case _ => state
