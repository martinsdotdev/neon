package neon.inbounddelivery

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

object InboundDeliveryActor:

  // --- Entity key for cluster sharding ---

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("InboundDelivery")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      delivery: InboundDelivery.New,
      event: InboundDeliveryEvent.InboundDeliveryCreated,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class StartReceiving(
      at: Instant,
      replyTo: ActorRef[StatusReply[StartReceivingResponse]]
  ) extends Command

  case class Receive(
      quantity: Int,
      rejectedQuantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[ReceiveResponse]]
  ) extends Command

  case class Complete(
      at: Instant,
      replyTo: ActorRef[StatusReply[CompleteResponse]]
  ) extends Command

  case class Close(
      at: Instant,
      replyTo: ActorRef[StatusReply[CloseResponse]]
  ) extends Command

  case class Cancel(
      at: Instant,
      replyTo: ActorRef[StatusReply[CancelResponse]]
  ) extends Command

  case class GetState(replyTo: ActorRef[Option[InboundDelivery]]) extends Command

  // --- Responses ---

  case class StartReceivingResponse(
      delivery: InboundDelivery.Receiving,
      event: InboundDeliveryEvent.ReceivingStarted
  ) extends CborSerializable

  case class ReceiveResponse(
      delivery: InboundDelivery,
      event: InboundDeliveryEvent.QuantityReceived
  ) extends CborSerializable

  case class CompleteResponse(
      delivery: InboundDelivery.Received,
      event: InboundDeliveryEvent.InboundDeliveryReceived
  ) extends CborSerializable

  case class CloseResponse(
      delivery: InboundDelivery.Closed,
      event: InboundDeliveryEvent.InboundDeliveryClosed
  ) extends CborSerializable

  case class CancelResponse(
      delivery: InboundDelivery.Cancelled,
      event: InboundDeliveryEvent.InboundDeliveryCancelled
  ) extends CborSerializable

  // --- Actor state ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(delivery: InboundDelivery) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map("entityType" -> "InboundDelivery", "entityId" -> entityId)
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[Command, InboundDeliveryEvent, State](
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
  ): (State, Command) => ReplyEffect[InboundDeliveryEvent, State] =
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

        case (ActiveState(d: InboundDelivery.New), StartReceiving(at, replyTo)) =>
          val (receiving, event) = d.startReceiving(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(StartReceivingResponse(receiving, event)))

        case (ActiveState(d: InboundDelivery.Receiving), Receive(qty, rejected, at, replyTo)) =>
          val (updated, event) = d.receive(qty, rejected, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(ReceiveResponse(updated, event)))

        case (ActiveState(d: InboundDelivery.Receiving), Complete(at, replyTo)) =>
          val (received, event) = d.complete(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CompleteResponse(received, event)))

        case (ActiveState(d: InboundDelivery.Receiving), Close(at, replyTo)) =>
          val (closed, event) = d.close(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CloseResponse(closed, event)))

        case (ActiveState(d: InboundDelivery.New), Cancel(at, replyTo)) =>
          val (cancelled, event) = d.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (_, GetState(replyTo)) =>
          val delivery = state match
            case EmptyState            => None
            case ActiveState(delivery) => Some(delivery)
          Effect.reply(replyTo)(delivery)

        case (_, cmd) =>
          val msg =
            s"Invalid command ${cmd.getClass.getSimpleName} " +
              s"in state ${state.getClass.getSimpleName}"
          context.log.warn(msg)
          cmd match
            case c: Create         => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: StartReceiving => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Receive        => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Complete       => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Close          => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Cancel         => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState       => Effect.reply(c.replyTo)(None)

  // --- Event handler (state recovery) ---

  private val eventHandler: (State, InboundDeliveryEvent) => State =
    (state, event) =>
      event match
        case e: InboundDeliveryEvent.InboundDeliveryCreated =>
          ActiveState(
            InboundDelivery.New(
              e.inboundDeliveryId,
              e.skuId,
              e.packagingLevel,
              e.lotAttributes,
              e.expectedQuantity
            )
          )
        case _: InboundDeliveryEvent.ReceivingStarted =>
          state match
            case ActiveState(d: InboundDelivery.New) =>
              ActiveState(
                InboundDelivery.Receiving(
                  d.id,
                  d.skuId,
                  d.packagingLevel,
                  d.lotAttributes,
                  d.expectedQuantity,
                  0,
                  0
                )
              )
            case _ => state
        case e: InboundDeliveryEvent.QuantityReceived =>
          state match
            case ActiveState(d: InboundDelivery.Receiving) =>
              ActiveState(
                d.copy(
                  receivedQuantity = d.receivedQuantity + e.quantity,
                  rejectedQuantity = d.rejectedQuantity + e.rejectedQuantity
                )
              )
            case _ => state
        case e: InboundDeliveryEvent.InboundDeliveryReceived =>
          state match
            case ActiveState(d: InboundDelivery.Receiving) =>
              ActiveState(
                InboundDelivery.Received(
                  d.id,
                  d.skuId,
                  d.packagingLevel,
                  d.lotAttributes,
                  d.expectedQuantity,
                  d.receivedQuantity,
                  d.rejectedQuantity
                )
              )
            case _ => state
        case e: InboundDeliveryEvent.InboundDeliveryClosed =>
          state match
            case ActiveState(d: InboundDelivery.Receiving) =>
              ActiveState(
                InboundDelivery.Closed(
                  d.id,
                  d.skuId,
                  d.packagingLevel,
                  d.lotAttributes,
                  d.expectedQuantity,
                  e.receivedQuantity,
                  e.rejectedQuantity
                )
              )
            case _ => state
        case _: InboundDeliveryEvent.InboundDeliveryCancelled =>
          state match
            case ActiveState(d: InboundDelivery.New) =>
              ActiveState(
                InboundDelivery.Cancelled(
                  d.id,
                  d.skuId,
                  d.packagingLevel,
                  d.lotAttributes,
                  d.expectedQuantity
                )
              )
            case _ => state
