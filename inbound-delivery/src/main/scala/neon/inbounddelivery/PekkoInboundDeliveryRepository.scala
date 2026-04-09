package neon.inbounddelivery

import neon.common.InboundDeliveryId
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncInboundDeliveryRepository]]. Single-entity operations
  * route to the InboundDeliveryActor via Cluster Sharding ask pattern.
  */
class PekkoInboundDeliveryRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncInboundDeliveryRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(InboundDeliveryActor.EntityKey)(ctx => InboundDeliveryActor(ctx.entityId))
  )

  def findById(id: InboundDeliveryId): Future[Option[InboundDelivery]] =
    sharding
      .entityRefFor(InboundDeliveryActor.EntityKey, id.value.toString)
      .ask(InboundDeliveryActor.GetState(_))

  def save(delivery: InboundDelivery, event: InboundDeliveryEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      InboundDeliveryActor.EntityKey,
      delivery.id.value.toString
    )
    event match
      case e: InboundDeliveryEvent.InboundDeliveryCreated =>
        entityRef
          .askWithStatus(
            InboundDeliveryActor.Create(
              InboundDelivery.New(
                e.inboundDeliveryId,
                e.skuId,
                e.packagingLevel,
                e.lotAttributes,
                e.expectedQuantity
              ),
              e,
              _
            )
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.ReceivingStarted =>
        entityRef
          .askWithStatus[InboundDeliveryActor.StartReceivingResponse](
            InboundDeliveryActor.StartReceiving(e.occurredAt, _)
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.QuantityReceived =>
        entityRef
          .askWithStatus[InboundDeliveryActor.ReceiveResponse](
            InboundDeliveryActor.Receive(e.quantity, e.rejectedQuantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.InboundDeliveryReceived =>
        entityRef
          .askWithStatus[InboundDeliveryActor.CompleteResponse](
            InboundDeliveryActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.InboundDeliveryClosed =>
        entityRef
          .askWithStatus[InboundDeliveryActor.CloseResponse](
            InboundDeliveryActor.Close(e.occurredAt, _)
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.InboundDeliveryCancelled =>
        entityRef
          .askWithStatus[InboundDeliveryActor.CancelResponse](
            InboundDeliveryActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
