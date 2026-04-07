package neon.handlingunit

import neon.common.LocationId
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

object HandlingUnitActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("HandlingUnit")

  /** Wrapper event type because HandlingUnit has no domain creation event. The `Initialized`
    * variant persists the initial state (PickCreated or ShipCreated) to the journal.
    */
  sealed trait ActorEvent extends CborSerializable
  case class Initialized(handlingUnit: HandlingUnit) extends ActorEvent
  case class DomainEvent(event: HandlingUnitEvent) extends ActorEvent

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      handlingUnit: HandlingUnit,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class MoveToBuffer(
      locationId: LocationId,
      at: Instant,
      replyTo: ActorRef[StatusReply[MoveToBufferResponse]]
  ) extends Command

  case class Empty(
      at: Instant,
      replyTo: ActorRef[StatusReply[EmptyResponse]]
  ) extends Command

  case class Pack(
      at: Instant,
      replyTo: ActorRef[StatusReply[PackResponse]]
  ) extends Command

  case class ReadyToShip(
      at: Instant,
      replyTo: ActorRef[StatusReply[ReadyToShipResponse]]
  ) extends Command

  case class Ship(
      at: Instant,
      replyTo: ActorRef[StatusReply[ShipResponse]]
  ) extends Command

  case class GetState(
      replyTo: ActorRef[Option[HandlingUnit]]
  ) extends Command

  // --- Responses ---

  case class MoveToBufferResponse(
      inBuffer: HandlingUnit.InBuffer,
      event: HandlingUnitEvent.HandlingUnitMovedToBuffer
  ) extends CborSerializable

  case class EmptyResponse(
      empty: HandlingUnit.Empty,
      event: HandlingUnitEvent.HandlingUnitEmptied
  ) extends CborSerializable

  case class PackResponse(
      packed: HandlingUnit.Packed,
      event: HandlingUnitEvent.HandlingUnitPacked
  ) extends CborSerializable

  case class ReadyToShipResponse(
      ready: HandlingUnit.ReadyToShip,
      event: HandlingUnitEvent.HandlingUnitReadyToShip
  ) extends CborSerializable

  case class ShipResponse(
      shipped: HandlingUnit.Shipped,
      event: HandlingUnitEvent.HandlingUnitShipped
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(handlingUnit: HandlingUnit) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, ActorEvent, State](
        persistenceId = PersistenceId(EntityKey.name, entityId),
        emptyState = EmptyState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
      .withTagger(_ => Set("handling-unit"))
      .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private val commandHandler: (State, Command) => ReplyEffect[ActorEvent, State] =
    (state, command) =>
      (state, command) match

        case (EmptyState, Create(handlingUnit, replyTo)) =>
          Effect
            .persist(Initialized(handlingUnit))
            .thenReply(replyTo)(_ => StatusReply.ack())

        case (
              ActiveState(pc: HandlingUnit.PickCreated),
              MoveToBuffer(locationId, at, replyTo)
            ) =>
          val (inBuffer, event) = pc.moveToBuffer(locationId, at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(MoveToBufferResponse(inBuffer, event)))

        case (
              ActiveState(ib: HandlingUnit.InBuffer),
              Empty(at, replyTo)
            ) =>
          val (empty, event) = ib.empty(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(EmptyResponse(empty, event)))

        case (
              ActiveState(sc: HandlingUnit.ShipCreated),
              Pack(at, replyTo)
            ) =>
          val (packed, event) = sc.pack(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(PackResponse(packed, event)))

        case (
              ActiveState(p: HandlingUnit.Packed),
              ReadyToShip(at, replyTo)
            ) =>
          val (ready, event) = p.readyToShip(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(ReadyToShipResponse(ready, event)))

        case (
              ActiveState(rts: HandlingUnit.ReadyToShip),
              Ship(at, replyTo)
            ) =>
          val (shipped, event) = rts.ship(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(ShipResponse(shipped, event)))

        case (_, GetState(replyTo)) =>
          val result = state match
            case EmptyState                => None
            case ActiveState(handlingUnit) => Some(handlingUnit)
          Effect.reply(replyTo)(result)

        case (_, cmd) =>
          val msg = s"Invalid command ${cmd.getClass.getSimpleName} " +
            s"in state ${state.getClass.getSimpleName}"
          cmd match
            case c: Create       => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: MoveToBuffer => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Empty        => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Pack         => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: ReadyToShip  => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Ship         => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState     => Effect.reply(c.replyTo)(None)

  // --- Event handler ---

  private val eventHandler: (State, ActorEvent) => State =
    (state, event) =>
      event match
        case Initialized(handlingUnit) => ActiveState(handlingUnit)
        case DomainEvent(domainEvent)  =>
          (state, domainEvent) match
            case (
                  ActiveState(pc: HandlingUnit.PickCreated),
                  e: HandlingUnitEvent.HandlingUnitMovedToBuffer
                ) =>
              ActiveState(HandlingUnit.InBuffer(pc.id, pc.packagingLevel, e.locationId))
            case (
                  ActiveState(_: HandlingUnit.InBuffer),
                  _: HandlingUnitEvent.HandlingUnitEmptied
                ) =>
              state match
                case ActiveState(ib: HandlingUnit.InBuffer) =>
                  ActiveState(HandlingUnit.Empty(ib.id, ib.packagingLevel))
                case _ => state
            case (
                  ActiveState(sc: HandlingUnit.ShipCreated),
                  _: HandlingUnitEvent.HandlingUnitPacked
                ) =>
              ActiveState(
                HandlingUnit.Packed(sc.id, sc.packagingLevel, sc.currentLocation, sc.orderId)
              )
            case (
                  ActiveState(p: HandlingUnit.Packed),
                  _: HandlingUnitEvent.HandlingUnitReadyToShip
                ) =>
              ActiveState(
                HandlingUnit.ReadyToShip(p.id, p.packagingLevel, p.currentLocation, p.orderId)
              )
            case (
                  ActiveState(rts: HandlingUnit.ReadyToShip),
                  _: HandlingUnitEvent.HandlingUnitShipped
                ) =>
              ActiveState(HandlingUnit.Shipped(rts.id, rts.packagingLevel, rts.orderId))
            case _ => state
