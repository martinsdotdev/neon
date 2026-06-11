package neon.transportorder

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{HandlingUnitId, R2dbcProjectionQueries, TransportOrderId}
import neon.transportorder.TransportOrderProjectionSchema.TransportOrderByHandlingUnit
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

class PekkoTransportOrderRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[TransportOrderActor.Command, TransportOrder](
      actorSystem = actorSystem,
      entityKey = TransportOrderActor.EntityKey,
      behaviorFactory = TransportOrderActor.apply,
      getState = TransportOrderActor.GetState.apply
    )
    with AsyncTransportOrderRepository
    with R2dbcProjectionQueries:

  def findById(id: TransportOrderId): Future[Option[TransportOrder]] =
    findByEntityId(id.value.toString)

  def findByHandlingUnitId(
      handlingUnitId: HandlingUnitId
  ): Future[List[TransportOrder]] =
    queryProjectionIds(
      sql = TransportOrderByHandlingUnit.SelectTransportOrderIdsByHandlingUnitId,
      param = handlingUnitId.value,
      idColumn = TransportOrderByHandlingUnit.TransportOrderId
    ).flatMap(ids =>
      Future
        .sequence(ids.map(id => findById(TransportOrderId(id))))
        .map(_.flatten)
    )

  def save(
      transportOrder: TransportOrder,
      event: TransportOrderEvent
  ): Future[Unit] =
    val ref = entityRef(transportOrder.id.value.toString)
    event match
      case e: TransportOrderEvent.TransportOrderCreated =>
        ref
          .askWithStatus(
            TransportOrderActor.Create(
              transportOrder.asInstanceOf[TransportOrder.Pending],
              e,
              _
            )
          )
          .map(_ => ())
      case e: TransportOrderEvent.TransportOrderConfirmed =>
        ref
          .askWithStatus[TransportOrderActor.ConfirmResponse](
            TransportOrderActor.Confirm(e.occurredAt, _)
          )
          .map(_ => ())
      case e: TransportOrderEvent.TransportOrderCancelled =>
        ref
          .askWithStatus[TransportOrderActor.CancelResponse](
            TransportOrderActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())

  def saveAll(
      entries: List[(TransportOrder, TransportOrderEvent)]
  ): Future[Unit] =
    sequenceSaves(entries)(save)
