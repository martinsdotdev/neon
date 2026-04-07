package neon.workstation

import neon.common.ConsolidationGroupId
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

object WorkstationActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Workstation")

  /** Wrapper event type because Workstation starts in Disabled with no domain creation event.
    */
  sealed trait ActorEvent extends CborSerializable
  case class Initialized(workstation: Workstation) extends ActorEvent
  case class DomainEvent(event: WorkstationEvent) extends ActorEvent

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      workstation: Workstation,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Enable(
      at: Instant,
      replyTo: ActorRef[StatusReply[EnableResponse]]
  ) extends Command

  case class Assign(
      consolidationGroupId: ConsolidationGroupId,
      at: Instant,
      replyTo: ActorRef[StatusReply[AssignResponse]]
  ) extends Command

  case class Release(
      at: Instant,
      replyTo: ActorRef[StatusReply[ReleaseResponse]]
  ) extends Command

  case class Disable(
      at: Instant,
      replyTo: ActorRef[StatusReply[DisableResponse]]
  ) extends Command

  case class GetState(
      replyTo: ActorRef[Option[Workstation]]
  ) extends Command

  // --- Responses ---

  case class EnableResponse(
      idle: Workstation.Idle,
      event: WorkstationEvent.WorkstationEnabled
  ) extends CborSerializable

  case class AssignResponse(
      active: Workstation.Active,
      event: WorkstationEvent.WorkstationAssigned
  ) extends CborSerializable

  case class ReleaseResponse(
      idle: Workstation.Idle,
      event: WorkstationEvent.WorkstationReleased
  ) extends CborSerializable

  case class DisableResponse(
      disabled: Workstation.Disabled,
      event: WorkstationEvent.WorkstationDisabled
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(workstation: Workstation) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, ActorEvent, State](
        persistenceId = PersistenceId(EntityKey.name, entityId),
        emptyState = EmptyState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
      .withTagger(_ => Set("workstation"))
      .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private val commandHandler: (State, Command) => ReplyEffect[ActorEvent, State] =
    (state, command) =>
      (state, command) match

        case (EmptyState, Create(workstation, replyTo)) =>
          Effect
            .persist(Initialized(workstation))
            .thenReply(replyTo)(_ => StatusReply.ack())

        case (
              ActiveState(d: Workstation.Disabled),
              Enable(at, replyTo)
            ) =>
          val (idle, event) = d.enable(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(EnableResponse(idle, event)))

        case (
              ActiveState(i: Workstation.Idle),
              Assign(consolidationGroupId, at, replyTo)
            ) =>
          val (active, event) = i.assign(consolidationGroupId, at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(AssignResponse(active, event)))

        case (
              ActiveState(a: Workstation.Active),
              Release(at, replyTo)
            ) =>
          val (idle, event) = a.release(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(ReleaseResponse(idle, event)))

        case (
              ActiveState(i: Workstation.Idle),
              Disable(at, replyTo)
            ) =>
          val (disabled, event) = i.disable(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(DisableResponse(disabled, event)))

        case (
              ActiveState(a: Workstation.Active),
              Disable(at, replyTo)
            ) =>
          val (disabled, event) = a.disable(at)
          Effect
            .persist(DomainEvent(event))
            .thenReply(replyTo)(_ => StatusReply.success(DisableResponse(disabled, event)))

        case (_, GetState(replyTo)) =>
          val result = state match
            case EmptyState               => None
            case ActiveState(workstation) => Some(workstation)
          Effect.reply(replyTo)(result)

        case (_, cmd) =>
          val msg = s"Invalid command ${cmd.getClass.getSimpleName} " +
            s"in state ${state.getClass.getSimpleName}"
          cmd match
            case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Enable   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Assign   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Release  => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Disable  => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState => Effect.reply(c.replyTo)(None)

  // --- Event handler ---

  private val eventHandler: (State, ActorEvent) => State =
    (state, event) =>
      event match
        case Initialized(workstation) => ActiveState(workstation)
        case DomainEvent(domainEvent) =>
          (state, domainEvent) match
            case (ActiveState(d: Workstation.Disabled), e: WorkstationEvent.WorkstationEnabled) =>
              ActiveState(Workstation.Idle(d.id, d.workstationType, e.slotCount))
            case (ActiveState(i: Workstation.Idle), e: WorkstationEvent.WorkstationAssigned) =>
              ActiveState(
                Workstation.Active(
                  i.id,
                  i.workstationType,
                  i.slotCount,
                  e.consolidationGroupId
                )
              )
            case (ActiveState(a: Workstation.Active), _: WorkstationEvent.WorkstationReleased) =>
              ActiveState(Workstation.Idle(a.id, a.workstationType, a.slotCount))
            case (ActiveState(workstation), _: WorkstationEvent.WorkstationDisabled) =>
              ActiveState(
                Workstation.Disabled(
                  workstation.id,
                  workstation.workstationType,
                  workstation.slotCount
                )
              )
            case _ => state
