package neon.inbounddelivery

import neon.common.InboundDeliveryId
import neon.common.entity.PekkoEntityRepository
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

/** Actor-backed implementation of [[AsyncInboundDeliveryRepository]]. Single-entity operations
  * route to the InboundDeliveryActor via Cluster Sharding ask pattern.
  */
class PekkoInboundDeliveryRepository(system: ActorSystem[?])(using Timeout)
    extends PekkoEntityRepository[InboundDeliveryActor.Command, InboundDelivery](
      actorSystem = system,
      entityKey = InboundDeliveryActor.EntityKey,
      behaviorFactory = InboundDeliveryActor.apply,
      getState = InboundDeliveryActor.GetState.apply
    )
    with AsyncInboundDeliveryRepository:

  def findById(id: InboundDeliveryId): Future[Option[InboundDelivery]] =
    findByEntityId(id.value.toString)

  def save(delivery: InboundDelivery, event: InboundDeliveryEvent): Future[Unit] =
    val ref = entityRef(delivery.id.value.toString)
    event match
      case e: InboundDeliveryEvent.InboundDeliveryCreated =>
        ref
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
        ref
          .askWithStatus[InboundDeliveryActor.StartReceivingResponse](
            InboundDeliveryActor.StartReceiving(e.occurredAt, _)
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.QuantityReceived =>
        ref
          .askWithStatus[InboundDeliveryActor.ReceiveResponse](
            InboundDeliveryActor.Receive(e.quantity, e.rejectedQuantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.InboundDeliveryReceived =>
        ref
          .askWithStatus[InboundDeliveryActor.CompleteResponse](
            InboundDeliveryActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.InboundDeliveryClosed =>
        ref
          .askWithStatus[InboundDeliveryActor.CloseResponse](
            InboundDeliveryActor.Close(e.occurredAt, _)
          )
          .map(_ => ())
      case e: InboundDeliveryEvent.InboundDeliveryCancelled =>
        ref
          .askWithStatus[InboundDeliveryActor.CancelResponse](
            InboundDeliveryActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
