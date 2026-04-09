package neon.goodsreceipt

import neon.common.GoodsReceiptId
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncGoodsReceiptRepository]]. Single-entity operations route
  * to the GoodsReceiptActor via Cluster Sharding ask pattern.
  */
class PekkoGoodsReceiptRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncGoodsReceiptRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(GoodsReceiptActor.EntityKey)(ctx => GoodsReceiptActor(ctx.entityId))
  )

  def findById(id: GoodsReceiptId): Future[Option[GoodsReceipt]] =
    sharding
      .entityRefFor(GoodsReceiptActor.EntityKey, id.value.toString)
      .ask(GoodsReceiptActor.GetState(_))

  def save(receipt: GoodsReceipt, event: GoodsReceiptEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      GoodsReceiptActor.EntityKey,
      receipt.id.value.toString
    )
    event match
      case e: GoodsReceiptEvent.GoodsReceiptCreated =>
        entityRef
          .askWithStatus(
            GoodsReceiptActor.Create(
              GoodsReceipt.Open(e.goodsReceiptId, e.inboundDeliveryId, List.empty),
              e,
              _
            )
          )
          .map(_ => ())
      case e: GoodsReceiptEvent.LineRecorded =>
        entityRef
          .askWithStatus[GoodsReceiptActor.RecordLineResponse](
            GoodsReceiptActor.RecordLine(e.line, e.occurredAt, _)
          )
          .map(_ => ())
      case e: GoodsReceiptEvent.GoodsReceiptConfirmed =>
        entityRef
          .askWithStatus[GoodsReceiptActor.ConfirmResponse](
            GoodsReceiptActor.Confirm(e.occurredAt, _)
          )
          .map(_ => ())
      case e: GoodsReceiptEvent.GoodsReceiptCancelled =>
        entityRef
          .askWithStatus[GoodsReceiptActor.CancelResponse](
            GoodsReceiptActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
