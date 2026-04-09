package neon.stockposition

import neon.common.{AdjustmentReasonCode, InventoryStatus, StockLockType}
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

object StockPositionActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("StockPosition")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      stockPosition: StockPosition,
      event: StockPositionEvent.Created,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Allocate(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class Deallocate(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class AddQuantity(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class ConsumeAllocated(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class Reserve(
      quantity: Int,
      lockType: StockLockType,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class ReleaseReservation(
      quantity: Int,
      lockType: StockLockType,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class Block(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class Unblock(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class Adjust(
      delta: Int,
      reasonCode: AdjustmentReasonCode,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class ChangeStatus(
      newStatus: InventoryStatus,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class GetState(
      replyTo: ActorRef[Option[StockPosition]]
  ) extends Command

  // --- Responses ---

  case class MutationResponse(
      stockPosition: StockPosition
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(stockPosition: StockPosition) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map(
        "entityType" -> "StockPosition",
        "entityId" -> entityId
      )
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[Command, StockPositionEvent, State](
            persistenceId = PersistenceId(EntityKey.name, entityId),
            emptyState = EmptyState,
            commandHandler = commandHandler(context),
            eventHandler = eventHandler
          )
          .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => ReplyEffect[StockPositionEvent, State] =
    (state, command) =>
      context.log.debug(
        "Received {} in state {}",
        command.getClass.getSimpleName,
        state.getClass.getSimpleName
      )
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect.persist(event).thenReply(replyTo)(_ => StatusReply.ack())

        case (ActiveState(sp), Allocate(quantity, at, replyTo)) =>
          val (updated, event) = sp.allocate(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), Deallocate(quantity, at, replyTo)) =>
          val (updated, event) = sp.deallocate(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), AddQuantity(quantity, at, replyTo)) =>
          val (updated, event) = sp.addQuantity(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), ConsumeAllocated(quantity, at, replyTo)) =>
          val (updated, event) = sp.consumeAllocated(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), Reserve(quantity, lockType, at, replyTo)) =>
          val (updated, event) = sp.reserve(quantity, lockType, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), ReleaseReservation(quantity, lockType, at, replyTo)) =>
          val (updated, event) = sp.releaseReservation(quantity, lockType, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), Block(quantity, at, replyTo)) =>
          val (updated, event) = sp.block(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), Unblock(quantity, at, replyTo)) =>
          val (updated, event) = sp.unblock(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), Adjust(delta, reasonCode, at, replyTo)) =>
          val (updated, event) = sp.adjust(delta, reasonCode, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(sp), ChangeStatus(newStatus, at, replyTo)) =>
          val (updated, event) = sp.changeStatus(newStatus, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (_, GetState(replyTo)) =>
          val sp = state match
            case EmptyState         => None
            case ActiveState(stock) => Some(stock)
          Effect.reply(replyTo)(sp)

        case (_, cmd) =>
          val msg =
            s"Invalid command ${cmd.getClass.getSimpleName} in state ${state.getClass.getSimpleName}"
          context.log.warn(msg)
          cmd match
            case c: Create             => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Allocate           => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Deallocate         => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: AddQuantity        => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: ConsumeAllocated   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Reserve            => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: ReleaseReservation => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Block              => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Unblock            => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Adjust             => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: ChangeStatus       => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState           => Effect.reply(c.replyTo)(None)

  // --- Event handler ---

  private val eventHandler: (State, StockPositionEvent) => State =
    (state, event) =>
      event match
        case e: StockPositionEvent.Created =>
          ActiveState(
            StockPosition(
              id = e.stockPositionId,
              skuId = e.skuId,
              warehouseAreaId = e.warehouseAreaId,
              lotAttributes = e.lotAttributes,
              status = InventoryStatus.Available,
              onHandQuantity = e.onHandQuantity,
              availableQuantity = e.onHandQuantity,
              allocatedQuantity = 0,
              reservedQuantity = 0,
              blockedQuantity = 0
            )
          )
        case e: StockPositionEvent.Allocated =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              availableQuantity = sp.availableQuantity - e.quantity,
              allocatedQuantity = sp.allocatedQuantity + e.quantity
            )
          )
        case e: StockPositionEvent.Deallocated =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              availableQuantity = sp.availableQuantity + e.quantity,
              allocatedQuantity = sp.allocatedQuantity - e.quantity
            )
          )
        case e: StockPositionEvent.QuantityAdded =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              onHandQuantity = sp.onHandQuantity + e.quantity,
              availableQuantity = sp.availableQuantity + e.quantity
            )
          )
        case e: StockPositionEvent.AllocatedConsumed =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              onHandQuantity = sp.onHandQuantity - e.quantity,
              allocatedQuantity = sp.allocatedQuantity - e.quantity
            )
          )
        case e: StockPositionEvent.Reserved =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              availableQuantity = sp.availableQuantity - e.quantity,
              reservedQuantity = sp.reservedQuantity + e.quantity
            )
          )
        case e: StockPositionEvent.ReservationReleased =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              availableQuantity = sp.availableQuantity + e.quantity,
              reservedQuantity = sp.reservedQuantity - e.quantity
            )
          )
        case e: StockPositionEvent.Blocked =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              availableQuantity = sp.availableQuantity - e.quantity,
              blockedQuantity = sp.blockedQuantity + e.quantity
            )
          )
        case e: StockPositionEvent.Unblocked =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              availableQuantity = sp.availableQuantity + e.quantity,
              blockedQuantity = sp.blockedQuantity - e.quantity
            )
          )
        case e: StockPositionEvent.Adjusted =>
          applyToStockPosition(state)(sp =>
            sp.copy(
              onHandQuantity = sp.onHandQuantity + e.delta,
              availableQuantity = sp.availableQuantity + e.delta
            )
          )
        case e: StockPositionEvent.StatusChanged =>
          applyToStockPosition(state)(sp => sp.copy(status = e.newStatus))

  private def applyToStockPosition(state: State)(
      f: StockPosition => StockPosition
  ): State =
    state match
      case ActiveState(sp) => ActiveState(f(sp))
      case _               => state
