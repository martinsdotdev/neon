package neon.app.projection

import neon.goodsreceipt.GoodsReceiptEvent
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Populates `goods_receipt_by_delivery` from goods receipt events. */
class GoodsReceiptProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[GoodsReceiptEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[GoodsReceiptEvent]
  ): Future[Done] =
    envelope.event match
      case e: GoodsReceiptEvent.GoodsReceiptCreated =>
        val stmt = session
          .createStatement(
            """INSERT INTO goods_receipt_by_delivery
              |  (goods_receipt_id, inbound_delivery_id, state)
              |VALUES ($1, $2, $3)
              |ON CONFLICT (goods_receipt_id) DO UPDATE SET state = $3""".stripMargin
          )
          .bind(0, e.goodsReceiptId.value)
          .bind(1, e.inboundDeliveryId.value)
          .bind(2, "Open")
        session.updateOne(stmt).map(_ => Done)

      case _: GoodsReceiptEvent.LineRecorded =>
        Future.successful(Done)

      case e: GoodsReceiptEvent.GoodsReceiptConfirmed =>
        updateState(session, e.goodsReceiptId.value, "Confirmed")

      case e: GoodsReceiptEvent.GoodsReceiptCancelled =>
        updateState(session, e.goodsReceiptId.value, "Cancelled")

  private def updateState(
      session: R2dbcSession,
      goodsReceiptId: UUID,
      state: String
  ): Future[Done] =
    val stmt = session
      .createStatement(
        "UPDATE goods_receipt_by_delivery SET state = $1 WHERE goods_receipt_id = $2"
      )
      .bind(0, state)
      .bind(1, goodsReceiptId)
    session.updateOne(stmt).map(_ => Done)
