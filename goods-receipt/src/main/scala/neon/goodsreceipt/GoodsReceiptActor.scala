package neon.goodsreceipt

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

object GoodsReceiptActor:

  // --- Entity key for cluster sharding ---

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("GoodsReceipt")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      receipt: GoodsReceipt.Open,
      event: GoodsReceiptEvent.GoodsReceiptCreated,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class RecordLine(
      line: ReceivedLine,
      at: Instant,
      replyTo: ActorRef[StatusReply[RecordLineResponse]]
  ) extends Command

  case class Confirm(
      at: Instant,
      replyTo: ActorRef[StatusReply[ConfirmResponse]]
  ) extends Command

  case class Cancel(
      at: Instant,
      replyTo: ActorRef[StatusReply[CancelResponse]]
  ) extends Command

  case class GetState(replyTo: ActorRef[Option[GoodsReceipt]]) extends Command

  // --- Responses ---

  case class RecordLineResponse(
      receipt: GoodsReceipt.Open,
      event: GoodsReceiptEvent.LineRecorded
  ) extends CborSerializable

  case class ConfirmResponse(
      receipt: GoodsReceipt.Confirmed,
      event: GoodsReceiptEvent.GoodsReceiptConfirmed
  ) extends CborSerializable

  case class CancelResponse(
      receipt: GoodsReceipt.Cancelled,
      event: GoodsReceiptEvent.GoodsReceiptCancelled
  ) extends CborSerializable

  // --- Actor state ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(receipt: GoodsReceipt) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map("entityType" -> "GoodsReceipt", "entityId" -> entityId)
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[Command, GoodsReceiptEvent, State](
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
  ): (State, Command) => ReplyEffect[GoodsReceiptEvent, State] =
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

        case (ActiveState(r: GoodsReceipt.Open), RecordLine(line, at, replyTo)) =>
          try
            val (updated, event) = r.recordLine(line, at)
            Effect
              .persist(event)
              .thenReply(replyTo)(_ => StatusReply.success(RecordLineResponse(updated, event)))
          catch
            case e: IllegalArgumentException =>
              Effect.reply(replyTo)(StatusReply.error(e.getMessage))

        case (ActiveState(r: GoodsReceipt.Open), Confirm(at, replyTo)) =>
          try
            val (confirmed, event) = r.confirm(at)
            Effect
              .persist(event)
              .thenReply(replyTo)(_ => StatusReply.success(ConfirmResponse(confirmed, event)))
          catch
            case e: IllegalArgumentException =>
              Effect.reply(replyTo)(StatusReply.error(e.getMessage))

        case (ActiveState(r: GoodsReceipt.Open), Cancel(at, replyTo)) =>
          val (cancelled, event) = r.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (_, GetState(replyTo)) =>
          val receipt = state match
            case EmptyState           => None
            case ActiveState(receipt) => Some(receipt)
          Effect.reply(replyTo)(receipt)

        case (_, cmd) =>
          val msg =
            s"Invalid command ${cmd.getClass.getSimpleName} " +
              s"in state ${state.getClass.getSimpleName}"
          context.log.warn(msg)
          cmd match
            case c: Create     => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: RecordLine => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Confirm    => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Cancel     => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState   => Effect.reply(c.replyTo)(None)

  // --- Event handler (state recovery) ---

  private val eventHandler: (State, GoodsReceiptEvent) => State =
    (state, event) =>
      event match
        case e: GoodsReceiptEvent.GoodsReceiptCreated =>
          ActiveState(
            GoodsReceipt.Open(e.goodsReceiptId, e.inboundDeliveryId, List.empty)
          )
        case e: GoodsReceiptEvent.LineRecorded =>
          state match
            case ActiveState(r: GoodsReceipt.Open) =>
              ActiveState(r.copy(receivedLines = r.receivedLines :+ e.line))
            case _ => state
        case e: GoodsReceiptEvent.GoodsReceiptConfirmed =>
          state match
            case ActiveState(r: GoodsReceipt.Open) =>
              ActiveState(
                GoodsReceipt.Confirmed(r.id, r.inboundDeliveryId, r.receivedLines)
              )
            case _ => state
        case _: GoodsReceiptEvent.GoodsReceiptCancelled =>
          state match
            case ActiveState(r: GoodsReceipt.Open) =>
              ActiveState(
                GoodsReceipt.Cancelled(r.id, r.inboundDeliveryId, r.receivedLines)
              )
            case _ => state
