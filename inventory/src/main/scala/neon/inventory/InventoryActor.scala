package neon.inventory

import neon.common.Lot
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
    Behaviors.withMdc[Command](
      Map(
        "entityType" -> "Inventory",
        "entityId" -> entityId
      )
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[
            Command,
            InventoryEvent,
            State
          ](
            persistenceId = PersistenceId(EntityKey.name, entityId),
            emptyState = EmptyState,
            commandHandler = commandHandler(context),
            eventHandler = eventHandler
          )
          .withTagger(_ => Set("inventory"))
          .withRetention(
            RetentionCriteria.snapshotEvery(100, 2)
          )

  // --- Command handler ---

  private def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => ReplyEffect[
    InventoryEvent,
    State
  ] =
    (state, command) =>
      context.log.debug(
        "Received {} in state {}",
        command.getClass.getSimpleName,
        state.getClass.getSimpleName
      )
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect.persist(event).thenReply(replyTo)(_ => StatusReply.ack())

        case (ActiveState(inventory), Reserve(quantity, at, replyTo)) =>
          val (updated, event) = inventory.reserve(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(inventory), Release(quantity, at, replyTo)) =>
          val (updated, event) = inventory.release(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(inventory), Consume(quantity, at, replyTo)) =>
          val (updated, event) = inventory.consume(quantity, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (ActiveState(inventory), CorrectLot(newLot, at, replyTo)) =>
          val (updated, event) = inventory.correctLot(newLot, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(MutationResponse(updated)))

        case (_, GetState(replyTo)) =>
          val inventory = state match
            case EmptyState             => None
            case ActiveState(inventory) => Some(inventory)
          Effect.reply(replyTo)(inventory)

        case (_, cmd) =>
          val msg =
            s"Invalid command " +
              s"${cmd.getClass.getSimpleName} " +
              s"in state ${state.getClass.getSimpleName}"
          context.log.warn(msg)
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
          applyToInventory(state)(inventory =>
            inventory.copy(reserved = inventory.reserved + e.quantityReserved)
          )
        case e: InventoryEvent.InventoryReleased =>
          applyToInventory(state)(inventory =>
            inventory.copy(reserved = inventory.reserved - e.quantityReleased)
          )
        case e: InventoryEvent.InventoryConsumed =>
          applyToInventory(state)(inventory =>
            inventory.copy(
              onHand = inventory.onHand - e.quantityConsumed,
              reserved = inventory.reserved - e.quantityConsumed
            )
          )
        case e: InventoryEvent.LotCorrected =>
          applyToInventory(state)(inventory => inventory.copy(lot = e.newLot))

  private def applyToInventory(state: State)(
      f: Inventory => Inventory
  ): State =
    state match
      case ActiveState(inventory) => ActiveState(f(inventory))
      case _                      => state
