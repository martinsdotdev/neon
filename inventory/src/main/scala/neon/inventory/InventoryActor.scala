package neon.inventory

import neon.common.Lot
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

object InventoryActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Inventory")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      inventory: Inventory,
      event: InventoryEvent.InventoryCreated,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Reserve(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class Release(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class Consume(
      quantity: Int,
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class CorrectLot(
      newLot: Option[Lot],
      at: Instant,
      replyTo: ActorRef[StatusReply[MutationResponse]]
  ) extends Command

  case class GetState(
      replyTo: ActorRef[Option[Inventory]]
  ) extends Command

  // --- Responses ---

  case class MutationResponse(
      inventory: Inventory
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(inventory: Inventory) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, InventoryEvent, State](
        persistenceId = PersistenceId(EntityKey.name, entityId),
        emptyState = EmptyState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
      .withTagger(_ => Set("inventory"))
      .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private val commandHandler: (State, Command) => ReplyEffect[InventoryEvent, State] =
    (state, command) =>
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect.persist(event).thenReply(replyTo)(_ => StatusReply.ack())

        case (ActiveState(inv), Reserve(qty, at, replyTo)) =>
          val (updated, event) = inv.reserve(qty, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(inv), Release(qty, at, replyTo)) =>
          val (updated, event) = inv.release(qty, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(inv), Consume(qty, at, replyTo)) =>
          val (updated, event) = inv.consume(qty, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(inv), CorrectLot(newLot, at, replyTo)) =>
          val (updated, event) = inv.correctLot(newLot, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (_, GetState(replyTo)) =>
          val inv = state match
            case EmptyState       => None
            case ActiveState(inv) => Some(inv)
          Effect.reply(replyTo)(inv)

        case (_, cmd) =>
          val msg = s"Invalid command ${cmd.getClass.getSimpleName} " +
            s"in state ${state.getClass.getSimpleName}"
          cmd match
            case c: Create     => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Reserve    => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Release    => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Consume    => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: CorrectLot => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState   => Effect.reply(c.replyTo)(None)

  // --- Event handler ---

  private val eventHandler: (State, InventoryEvent) => State =
    (state, event) =>
      event match
        case e: InventoryEvent.InventoryCreated =>
          ActiveState(
            Inventory(
              e.inventoryId,
              e.locationId,
              e.skuId,
              e.packagingLevel,
              e.lot,
              e.onHand,
              reserved = 0
            )
          )
        case e: InventoryEvent.InventoryReserved =>
          applyToInventory(state)(inv => inv.copy(reserved = inv.reserved + e.quantityReserved))
        case e: InventoryEvent.InventoryReleased =>
          applyToInventory(state)(inv => inv.copy(reserved = inv.reserved - e.quantityReleased))
        case e: InventoryEvent.InventoryConsumed =>
          applyToInventory(state)(inv =>
            inv.copy(
              onHand = inv.onHand - e.quantityConsumed,
              reserved = inv.reserved - e.quantityConsumed
            )
          )
        case e: InventoryEvent.LotCorrected =>
          applyToInventory(state)(inv => inv.copy(lot = e.newLot))

  private def applyToInventory(state: State)(
      f: Inventory => Inventory
  ): State =
    state match
      case ActiveState(inv) => ActiveState(f(inv))
      case _                => state
