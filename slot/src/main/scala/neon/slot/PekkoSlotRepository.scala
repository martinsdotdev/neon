package neon.slot

import io.r2dbc.spi.ConnectionFactory
import neon.common.{R2dbcProjectionQueries, SlotId, WorkstationId}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

class PekkoSlotRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncSlotRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
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
    queryProjectionIds(
      "SELECT slot_id FROM slot_by_workstation WHERE workstation_id = $1",
      workstationId.value,
      "slot_id"
    ).flatMap(ids => Future.sequence(ids.map(id => findById(SlotId(id)))).map(_.flatten))

  def save(slot: Slot, event: SlotEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      SlotActor.EntityKey,
      slot.id.value.toString
    )
    val ensureInitialized =
      entityRef.askWithStatus(SlotActor.Create(slot, _))
    ensureInitialized.flatMap { _ =>
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
    }

  def saveAll(entries: List[(Slot, SlotEvent)]): Future[Unit] =
    Future.sequence(entries.map((slot, event) => save(slot, event))).map(_ => ())
