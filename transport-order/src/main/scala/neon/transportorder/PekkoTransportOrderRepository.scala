package neon.transportorder

import neon.common.{HandlingUnitId, TransportOrderId}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncTransportOrderRepository]]. */
class PekkoTransportOrderRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncTransportOrderRepository:

  private given ExecutionContext = system.executionContext
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
    // TODO: query transport_order_by_handling_unit projection table
    Future.successful(Nil)

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
