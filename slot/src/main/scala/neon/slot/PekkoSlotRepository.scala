package neon.slot

import neon.common.{SlotId, WorkstationId}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncSlotRepository]]. */
class PekkoSlotRepository(system: ActorSystem[?])(using Timeout) extends AsyncSlotRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(SlotActor.EntityKey)(ctx => SlotActor(ctx.entityId))
  )

  def findById(id: SlotId): Future[Option[Slot]] =
    sharding
      .entityRefFor(SlotActor.EntityKey, id.value.toString)
      .ask(SlotActor.GetState(_))

  def findByWorkstationId(
      workstationId: WorkstationId
  ): Future[List[Slot]] =
    // TODO: query slot_by_workstation projection table
    Future.successful(Nil)

  def save(slot: Slot, event: SlotEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      SlotActor.EntityKey,
      slot.id.value.toString
    )
    event match
      case e: SlotEvent.SlotReserved =>
        entityRef
          .askWithStatus[SlotActor.ReserveResponse](
            SlotActor.Reserve(e.orderId, e.handlingUnitId, e.occurredAt, _)
          )
          .map(_ => ())
      case e: SlotEvent.SlotCompleted =>
        entityRef
          .askWithStatus[SlotActor.CompleteResponse](
            SlotActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: SlotEvent.SlotReleased =>
        entityRef
          .askWithStatus[SlotActor.ReleaseResponse](
            SlotActor.Release(e.occurredAt, _)
          )
          .map(_ => ())

  def saveAll(entries: List[(Slot, SlotEvent)]): Future[Unit] =
    Future.traverse(entries)((slot, event) => save(slot, event)).map(_ => ())
