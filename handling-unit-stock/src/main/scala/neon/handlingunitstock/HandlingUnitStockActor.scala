package neon.handlingunitstock

import neon.common.serialization.CborSerializable
import neon.common.{AdjustmentReasonCode, InventoryStatus, StockLockType}
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

object HandlingUnitStockActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("HandlingUnitStock")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      handlingUnitStock: HandlingUnitStock,
      event: HandlingUnitStockEvent.Created,
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
      replyTo: ActorRef[Option[HandlingUnitStock]]
  ) extends Command

  // --- Responses ---

  case class MutationResponse(
      handlingUnitStock: HandlingUnitStock
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(handlingUnitStock: HandlingUnitStock) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map(
        "entityType" -> "HandlingUnitStock",
        "entityId" -> entityId
      )
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[Command, HandlingUnitStockEvent, State](
            persistenceId = PersistenceId(EntityKey.name, entityId),
            emptyState = EmptyState,
            commandHandler = commandHandler(context),
            eventHandler = eventHandler
          )
          .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => ReplyEffect[HandlingUnitStockEvent, State] =
    (state, command) =>
      context.log.debug(
        "Received {} in state {}",
        command.getClass.getSimpleName,
        state.getClass.getSimpleName
      )
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect.persist(event).thenReply(replyTo)(_ => StatusReply.ack())

        case (ActiveState(hus), Allocate(quantity, at, replyTo)) =>
          val (updated, event) = hus.allocate(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), Deallocate(quantity, at, replyTo)) =>
          val (updated, event) = hus.deallocate(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), AddQuantity(quantity, at, replyTo)) =>
          val (updated, event) = hus.addQuantity(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), ConsumeAllocated(quantity, at, replyTo)) =>
          val (updated, event) = hus.consumeAllocated(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), Reserve(quantity, lockType, at, replyTo)) =>
          val (updated, event) = hus.reserve(quantity, lockType, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), ReleaseReservation(quantity, lockType, at, replyTo)) =>
          val (updated, event) = hus.releaseReservation(quantity, lockType, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), Block(quantity, at, replyTo)) =>
          val (updated, event) = hus.block(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), Unblock(quantity, at, replyTo)) =>
          val (updated, event) = hus.unblock(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), Adjust(delta, reasonCode, at, replyTo)) =>
          val (updated, event) = hus.adjust(delta, reasonCode, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(hus), ChangeStatus(newStatus, at, replyTo)) =>
          val (updated, event) = hus.changeStatus(newStatus, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (_, GetState(replyTo)) =>
          val hus = state match
            case EmptyState         => None
            case ActiveState(stock) => Some(stock)
          Effect.reply(replyTo)(hus)

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

  private val eventHandler: (State, HandlingUnitStockEvent) => State =
    (state, event) =>
      event match
        case e: HandlingUnitStockEvent.Created =>
          ActiveState(
            HandlingUnitStock(
              id = e.handlingUnitStockId,
              skuId = e.skuId,
              containerId = e.containerId,
              slotCode = e.slotCode,
              stockPositionId = e.stockPositionId,
              physicalContainer = e.physicalContainer,
              status = InventoryStatus.Available,
              onHandQuantity = e.onHandQuantity,
              availableQuantity = e.onHandQuantity,
              allocatedQuantity = 0,
              reservedQuantity = 0,
              blockedQuantity = 0
            )
          )
        case e: HandlingUnitStockEvent.Allocated =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              availableQuantity = hus.availableQuantity - e.quantity,
              allocatedQuantity = hus.allocatedQuantity + e.quantity
            )
          )
        case e: HandlingUnitStockEvent.Deallocated =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              availableQuantity = hus.availableQuantity + e.quantity,
              allocatedQuantity = hus.allocatedQuantity - e.quantity
            )
          )
        case e: HandlingUnitStockEvent.QuantityAdded =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              onHandQuantity = hus.onHandQuantity + e.quantity,
              availableQuantity = hus.availableQuantity + e.quantity
            )
          )
        case e: HandlingUnitStockEvent.AllocatedConsumed =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              onHandQuantity = hus.onHandQuantity - e.quantity,
              allocatedQuantity = hus.allocatedQuantity - e.quantity
            )
          )
        case e: HandlingUnitStockEvent.Reserved =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              availableQuantity = hus.availableQuantity - e.quantity,
              reservedQuantity = hus.reservedQuantity + e.quantity
            )
          )
        case e: HandlingUnitStockEvent.ReservationReleased =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              availableQuantity = hus.availableQuantity + e.quantity,
              reservedQuantity = hus.reservedQuantity - e.quantity
            )
          )
        case e: HandlingUnitStockEvent.Blocked =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              availableQuantity = hus.availableQuantity - e.quantity,
              blockedQuantity = hus.blockedQuantity + e.quantity
            )
          )
        case e: HandlingUnitStockEvent.Unblocked =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              availableQuantity = hus.availableQuantity + e.quantity,
              blockedQuantity = hus.blockedQuantity - e.quantity
            )
          )
        case e: HandlingUnitStockEvent.Adjusted =>
          applyToHandlingUnitStock(state)(hus =>
            hus.copy(
              onHandQuantity = hus.onHandQuantity + e.delta,
              availableQuantity = hus.availableQuantity + e.delta
            )
          )
        case e: HandlingUnitStockEvent.StatusChanged =>
          applyToHandlingUnitStock(state)(hus => hus.copy(status = e.newStatus))

  private def applyToHandlingUnitStock(state: State)(
      f: HandlingUnitStock => HandlingUnitStock
  ): State =
    state match
      case ActiveState(hus) => ActiveState(f(hus))
      case _                => state
