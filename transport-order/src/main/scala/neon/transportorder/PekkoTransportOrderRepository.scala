package neon.transportorder

import neon.common.{HandlingUnitId, R2dbcProjectionQueries, TransportOrderId}

import io.r2dbc.spi.ConnectionFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PekkoTransportOrderRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncTransportOrderRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(TransportOrderActor.EntityKey)(ctx => TransportOrderActor(ctx.entityId))
  )

  def findById(id: TransportOrderId): Future[Option[TransportOrder]] =
    sharding
      .entityRefFor(TransportOrderActor.EntityKey, id.value.toString)
      .ask(TransportOrderActor.GetState(_))

  def findByHandlingUnitId(
      handlingUnitId: HandlingUnitId
  ): Future[List[TransportOrder]] =
    queryProjectionIds(
      "SELECT transport_order_id FROM transport_order_by_handling_unit WHERE handling_unit_id = $1",
      handlingUnitId.value,
      "transport_order_id"
    ).flatMap(ids =>
      Future
        .sequence(ids.map(id => findById(TransportOrderId(id))))
        .map(_.flatten)
    )

  def save(
      transportOrder: TransportOrder,
      event: TransportOrderEvent
  ): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      TransportOrderActor.EntityKey,
      transportOrder.id.value.toString
    )
    event match
      case e: TransportOrderEvent.TransportOrderCreated =>
        entityRef
          .askWithStatus(
            TransportOrderActor.Create(
              transportOrder.asInstanceOf[TransportOrder.Pending],
              e,
              _
            )
          )
          .map(_ => ())
      case e: TransportOrderEvent.TransportOrderConfirmed =>
        entityRef
          .askWithStatus[TransportOrderActor.ConfirmResponse](
            TransportOrderActor.Confirm(e.occurredAt, _)
          )
          .map(_ => ())
      case e: TransportOrderEvent.TransportOrderCancelled =>
        entityRef
          .askWithStatus[TransportOrderActor.CancelResponse](
            TransportOrderActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())

  def saveAll(
      entries: List[(TransportOrder, TransportOrderEvent)]
  ): Future[Unit] =
    Future.sequence(entries.map((to, event) => save(to, event))).map(_ => ())
