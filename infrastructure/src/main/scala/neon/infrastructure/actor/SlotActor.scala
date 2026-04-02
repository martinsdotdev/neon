package neon.infrastructure.actor

import neon.common.{HandlingUnitId, OrderId}
import neon.infrastructure.serialization.CborSerializable
import neon.slot.{Slot, SlotEvent}

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

object SlotActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Slot")

  /** Wrapper event type because Slot starts in Available with no domain creation event.
    */
  sealed trait ActorEvent extends CborSerializable
  case class Initialized(slot: Slot) extends ActorEvent
  case class DomainEvent(event: SlotEvent) extends ActorEvent

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      slot: Slot,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Reserve(
      orderId: OrderId,
      handlingUnitId: HandlingUnitId,
      at: Instant,
      replyTo: ActorRef[StatusReply[ReserveResponse]]
  ) extends Command

  case class Complete(
      at: Instant,
      replyTo: ActorRef[StatusReply[CompleteResponse]]
  ) extends Command

  case class Release(
      at: Instant,
      replyTo: ActorRef[StatusReply[ReleaseResponse]]
  ) extends Command

  case class GetState(
      replyTo: ActorRef[Option[Slot]]
  ) extends Command

  // --- Responses ---

  case class ReserveResponse(
      reserved: Slot.Reserved,
      event: SlotEvent.SlotReserved
  ) extends CborSerializable

  case class CompleteResponse(
      completed: Slot.Completed,
      event: SlotEvent.SlotCompleted
  ) extends CborSerializable

  case class ReleaseResponse(
      available: Slot.Available,
      event: SlotEvent.SlotReleased
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(slot: Slot) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, ActorEvent, State](
        persistenceId = PersistenceId(EntityKey.name, entityId),
        emptyState = EmptyState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
      .withTagger(_ => Set("slot"))
      .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private val commandHandler: (State, Command) => ReplyEffect[ActorEvent, State] =
    (state, command) =>
      (state, command) match

        case (EmptyState, Create(slot, replyTo)) =>
          Effect
            .persist(Initialized(slot))
            .thenReply(replyTo)(_ => StatusReply.ack())

        case (
              ActiveState(a: Slot.Available),
              Reserve(orderId, huId, at, replyTo)
            ) =>
          val (reserved, event) = a.reserve(orderId, huId, at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(ReserveResponse(reserved, event)))

        case (
              ActiveState(r: Slot.Reserved),
              Complete(at, replyTo)
            ) =>
          val (completed, event) = r.complete(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(CompleteResponse(completed, event)))

        case (
              ActiveState(r: Slot.Reserved),
              Release(at, replyTo)
            ) =>
          val (available, event) = r.release(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(ReleaseResponse(available, event)))

        case (_, GetState(replyTo)) =>
          val slot = state match
            case EmptyState        => None
            case ActiveState(slot) => Some(slot)
          Effect.reply(replyTo)(slot)

        case (_, cmd) =>
          val msg = s"Invalid command ${cmd.getClass.getSimpleName} " +
            s"in state ${state.getClass.getSimpleName}"
          cmd match
            case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Reserve  => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Complete => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Release  => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState => Effect.reply(c.replyTo)(None)

  // --- Event handler ---

  private val eventHandler: (State, ActorEvent) => State =
    (state, event) =>
      event match
        case Initialized(slot)        => ActiveState(slot)
        case DomainEvent(domainEvent) =>
          (state, domainEvent) match
            case (_, e: SlotEvent.SlotReserved) =>
              ActiveState(
                Slot.Reserved(
                  e.slotId,
                  e.workstationId,
                  e.orderId,
                  e.handlingUnitId
                )
              )
            case (ActiveState(r: Slot.Reserved), e: SlotEvent.SlotCompleted) =>
              ActiveState(
                Slot.Completed(
                  r.id,
                  r.workstationId,
                  r.orderId,
                  r.handlingUnitId
                )
              )
            case (_, e: SlotEvent.SlotReleased) =>
              ActiveState(Slot.Available(e.slotId, e.workstationId))
            case _ => state
