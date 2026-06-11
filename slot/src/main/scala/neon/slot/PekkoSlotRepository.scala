package neon.slot

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{R2dbcProjectionQueries, SlotId, WorkstationId}
import neon.slot.SlotProjectionSchema.SlotByWorkstation
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

class PekkoSlotRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[SlotActor.Command, Slot](
      actorSystem = actorSystem,
      entityKey = SlotActor.EntityKey,
      behaviorFactory = SlotActor.apply,
      getState = SlotActor.GetState.apply
    )
    with AsyncSlotRepository
    with R2dbcProjectionQueries:

  def findById(id: SlotId): Future[Option[Slot]] =
    findByEntityId(id.value.toString)

  def findByWorkstationId(
      workstationId: WorkstationId
  ): Future[List[Slot]] =
    queryProjectionIds(
      sql = SlotByWorkstation.SelectSlotIdsByWorkstationId,
      param = workstationId.value,
      idColumn = SlotByWorkstation.SlotId
    ).flatMap(ids => Future.sequence(ids.map(id => findById(SlotId(id)))).map(_.flatten))

  def save(slot: Slot, event: SlotEvent): Future[Unit] =
    val ref = entityRef(slot.id.value.toString)
    val ensureInitialized =
      ref.askWithStatus(SlotActor.Create(slot, _))
    ensureInitialized.flatMap { _ =>
      event match
        case e: SlotEvent.SlotReserved =>
          ref
            .askWithStatus[SlotActor.ReserveResponse](
              SlotActor.Reserve(e.orderId, e.handlingUnitId, e.occurredAt, _)
            )
            .map(_ => ())
        case e: SlotEvent.SlotCompleted =>
          ref
            .askWithStatus[SlotActor.CompleteResponse](
              SlotActor.Complete(e.occurredAt, _)
            )
            .map(_ => ())
        case e: SlotEvent.SlotReleased =>
          ref
            .askWithStatus[SlotActor.ReleaseResponse](
              SlotActor.Release(e.occurredAt, _)
            )
            .map(_ => ())
    }

  def saveAll(entries: List[(Slot, SlotEvent)]): Future[Unit] =
    sequenceSaves(entries)(save)
