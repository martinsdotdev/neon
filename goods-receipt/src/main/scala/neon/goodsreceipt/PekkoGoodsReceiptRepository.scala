package neon.goodsreceipt

import neon.common.GoodsReceiptId
import neon.common.entity.PekkoEntityRepository
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

/** Actor-backed implementation of [[AsyncGoodsReceiptRepository]]. Single-entity operations route
  * to the GoodsReceiptActor via Cluster Sharding ask pattern.
  */
class PekkoGoodsReceiptRepository(system: ActorSystem[?])(using Timeout)
    extends PekkoEntityRepository[GoodsReceiptActor.Command, GoodsReceipt](
      actorSystem = system,
      entityKey = GoodsReceiptActor.EntityKey,
      behaviorFactory = GoodsReceiptActor.apply,
      getState = GoodsReceiptActor.GetState.apply
    )
    with AsyncGoodsReceiptRepository:

  def findById(id: GoodsReceiptId): Future[Option[GoodsReceipt]] =
    findByEntityId(id.value.toString)

  def save(receipt: GoodsReceipt, event: GoodsReceiptEvent): Future[Unit] =
    val ref = entityRef(receipt.id.value.toString)
    event match
      case e: GoodsReceiptEvent.GoodsReceiptCreated =>
        ref
          .askWithStatus(
            GoodsReceiptActor.Create(
              GoodsReceipt.Open(e.goodsReceiptId, e.inboundDeliveryId, List.empty),
              e,
              _
            )
          )
          .map(_ => ())
      case e: GoodsReceiptEvent.LineRecorded =>
        ref
          .askWithStatus[GoodsReceiptActor.RecordLineResponse](
            GoodsReceiptActor.RecordLine(e.line, e.occurredAt, _)
          )
          .map(_ => ())
      case e: GoodsReceiptEvent.GoodsReceiptConfirmed =>
        ref
          .askWithStatus[GoodsReceiptActor.ConfirmResponse](
            GoodsReceiptActor.Confirm(e.occurredAt, _)
          )
          .map(_ => ())
      case e: GoodsReceiptEvent.GoodsReceiptCancelled =>
        ref
          .askWithStatus[GoodsReceiptActor.CancelResponse](
            GoodsReceiptActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
