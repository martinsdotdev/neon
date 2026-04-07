package neon.consolidationgroup

import neon.common.WorkstationId
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

object ConsolidationGroupActor:

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("ConsolidationGroup")

  // --- Commands ---

  sealed trait Command extends CborSerializable

  case class Create(
      created: ConsolidationGroup.Created,
      event: ConsolidationGroupEvent.ConsolidationGroupCreated,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Pick(
      at: Instant,
      replyTo: ActorRef[StatusReply[PickResponse]]
  ) extends Command

  case class ReadyForWorkstation(
      at: Instant,
      replyTo: ActorRef[StatusReply[ReadyForWorkstationResponse]]
  ) extends Command

  case class Assign(
      workstationId: WorkstationId,
      at: Instant,
      replyTo: ActorRef[StatusReply[AssignResponse]]
  ) extends Command

  case class Complete(
      at: Instant,
      replyTo: ActorRef[StatusReply[CompleteResponse]]
  ) extends Command

  case class Cancel(
      at: Instant,
      replyTo: ActorRef[StatusReply[CancelResponse]]
  ) extends Command

  case class GetState(
      replyTo: ActorRef[Option[ConsolidationGroup]]
  ) extends Command

  // --- Responses ---

  case class PickResponse(
      picked: ConsolidationGroup.Picked,
      event: ConsolidationGroupEvent.ConsolidationGroupPicked
  ) extends CborSerializable

  case class ReadyForWorkstationResponse(
      ready: ConsolidationGroup.ReadyForWorkstation,
      event: ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation
  ) extends CborSerializable

  case class AssignResponse(
      assigned: ConsolidationGroup.Assigned,
      event: ConsolidationGroupEvent.ConsolidationGroupAssigned
  ) extends CborSerializable

  case class CompleteResponse(
      completed: ConsolidationGroup.Completed,
      event: ConsolidationGroupEvent.ConsolidationGroupCompleted
  ) extends CborSerializable

  case class CancelResponse(
      cancelled: ConsolidationGroup.Cancelled,
      event: ConsolidationGroupEvent.ConsolidationGroupCancelled
  ) extends CborSerializable

  // --- State ---

  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(group: ConsolidationGroup) extends State

  // --- Behavior ---

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, ConsolidationGroupEvent, State](
        persistenceId = PersistenceId(EntityKey.name, entityId),
        emptyState = EmptyState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
      .withTagger(_ => Set("consolidation-group"))
      .withRetention(RetentionCriteria.snapshotEvery(100, 2))

  // --- Command handler ---

  private val commandHandler: (State, Command) => ReplyEffect[ConsolidationGroupEvent, State] =
    (state, command) =>
      (state, command) match

        case (EmptyState, Create(_, event, replyTo)) =>
          Effect.persist(event).thenReply(replyTo)(_ => StatusReply.ack())

        case (ActiveState(c: ConsolidationGroup.Created), Pick(at, replyTo)) =>
          val (picked, event) = c.pick(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(PickResponse(picked, event)))

        case (
              ActiveState(p: ConsolidationGroup.Picked),
              ReadyForWorkstation(at, replyTo)
            ) =>
          val (ready, event) = p.readyForWorkstation(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ =>
              StatusReply.success(
                ReadyForWorkstationResponse(ready, event)
              )
            )

        case (
              ActiveState(r: ConsolidationGroup.ReadyForWorkstation),
              Assign(wsId, at, replyTo)
            ) =>
          val (assigned, event) = r.assign(wsId, at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(AssignResponse(assigned, event)))

        case (
              ActiveState(a: ConsolidationGroup.Assigned),
              Complete(at, replyTo)
            ) =>
          val (completed, event) = a.complete(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CompleteResponse(completed, event)))

        case (ActiveState(c: ConsolidationGroup.Created), Cancel(at, replyTo)) =>
          val (cancelled, event) = c.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (ActiveState(p: ConsolidationGroup.Picked), Cancel(at, replyTo)) =>
          val (cancelled, event) = p.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (
              ActiveState(r: ConsolidationGroup.ReadyForWorkstation),
              Cancel(at, replyTo)
            ) =>
          val (cancelled, event) = r.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (
              ActiveState(a: ConsolidationGroup.Assigned),
              Cancel(at, replyTo)
            ) =>
          val (cancelled, event) = a.cancel(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.success(CancelResponse(cancelled, event)))

        case (_, GetState(replyTo)) =>
          val group = state match
            case EmptyState         => None
            case ActiveState(group) => Some(group)
          Effect.reply(replyTo)(group)

        case (_, cmd) =>
          val msg = s"Invalid command ${cmd.getClass.getSimpleName} " +
            s"in state ${state.getClass.getSimpleName}"
          cmd match
            case c: Create              => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Pick                => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: ReadyForWorkstation => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Assign              => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Complete            => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Cancel              => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState            => Effect.reply(c.replyTo)(None)

  // --- Event handler ---

  private val eventHandler: (State, ConsolidationGroupEvent) => State =
    (state, event) =>
      event match
        case e: ConsolidationGroupEvent.ConsolidationGroupCreated =>
          ActiveState(
            ConsolidationGroup.Created(
              e.consolidationGroupId,
              e.waveId,
              e.orderIds
            )
          )
        case _: ConsolidationGroupEvent.ConsolidationGroupPicked =>
          state match
            case ActiveState(c: ConsolidationGroup.Created) =>
              ActiveState(
                ConsolidationGroup.Picked(c.id, c.waveId, c.orderIds)
              )
            case _ => state
        case _: ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation =>
          state match
            case ActiveState(p: ConsolidationGroup.Picked) =>
              ActiveState(
                ConsolidationGroup.ReadyForWorkstation(
                  p.id,
                  p.waveId,
                  p.orderIds
                )
              )
            case _ => state
        case e: ConsolidationGroupEvent.ConsolidationGroupAssigned =>
          state match
            case ActiveState(r: ConsolidationGroup.ReadyForWorkstation) =>
              ActiveState(
                ConsolidationGroup.Assigned(
                  r.id,
                  r.waveId,
                  r.orderIds,
                  e.workstationId
                )
              )
            case _ => state
        case _: ConsolidationGroupEvent.ConsolidationGroupCompleted =>
          state match
            case ActiveState(a: ConsolidationGroup.Assigned) =>
              ActiveState(
                ConsolidationGroup.Completed(
                  a.id,
                  a.waveId,
                  a.orderIds,
                  a.workstationId
                )
              )
            case _ => state
        case _: ConsolidationGroupEvent.ConsolidationGroupCancelled =>
          state match
            case ActiveState(g) =>
              ActiveState(
                ConsolidationGroup.Cancelled(g.id, g.waveId, g.orderIds)
              )
            case _ => state
