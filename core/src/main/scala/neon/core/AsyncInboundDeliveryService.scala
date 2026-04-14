package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.{GoodsReceiptId, InboundDeliveryId, LotAttributes, PackagingLevel, SkuId}
import neon.goodsreceipt.{
  AsyncGoodsReceiptRepository,
  GoodsReceipt,
  GoodsReceiptEvent,
  ReceivedLine
}
import neon.inbounddelivery.{AsyncInboundDeliveryRepository, InboundDelivery, InboundDeliveryEvent}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait InboundDeliveryError
object InboundDeliveryError:
  case class DeliveryNotFound(id: InboundDeliveryId) extends InboundDeliveryError
  case class DeliveryInWrongState(id: InboundDeliveryId) extends InboundDeliveryError
  case class ReceiptNotFound(id: GoodsReceiptId) extends InboundDeliveryError
  case class ReceiptInWrongState(id: GoodsReceiptId) extends InboundDeliveryError

case class InboundDeliveryCreateResult(
    delivery: InboundDelivery.New,
    event: InboundDeliveryEvent.InboundDeliveryCreated
)
case class InboundDeliveryReceiveResult(
    delivery: InboundDelivery.Receiving,
    event: InboundDeliveryEvent.QuantityReceived
)
case class GoodsReceiptCreateResult(
    receipt: GoodsReceipt.Open,
    event: GoodsReceiptEvent.GoodsReceiptCreated
)
case class GoodsReceiptRecordLineResult(
    receipt: GoodsReceipt.Open,
    event: GoodsReceiptEvent.LineRecorded
)
case class GoodsReceiptConfirmResult(
    receipt: GoodsReceipt.Confirmed,
    event: GoodsReceiptEvent.GoodsReceiptConfirmed
)

class AsyncInboundDeliveryService(
    inboundDeliveryRepository: AsyncInboundDeliveryRepository,
    goodsReceiptRepository: AsyncGoodsReceiptRepository
)(using ExecutionContext)
    extends LazyLogging:

  def createDelivery(
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lotAttributes: LotAttributes,
      expectedQuantity: Int,
      at: Instant
  ): Future[Either[InboundDeliveryError, InboundDeliveryCreateResult]] =
    val id = InboundDeliveryId()
    val delivery =
      InboundDelivery.New(id, skuId, packagingLevel, lotAttributes, expectedQuantity)
    val event = InboundDeliveryEvent.InboundDeliveryCreated(
      id,
      skuId,
      packagingLevel,
      lotAttributes,
      expectedQuantity,
      at
    )
    inboundDeliveryRepository
      .save(delivery, event)
      .map(_ => Right(InboundDeliveryCreateResult(delivery, event)))

  def receiveQuantity(
      id: InboundDeliveryId,
      quantity: Int,
      rejectedQuantity: Int,
      at: Instant
  ): Future[Either[InboundDeliveryError, InboundDeliveryReceiveResult]] =
    inboundDeliveryRepository
      .findById(id)
      .flatMap:
        case None =>
          Future.successful(Left(InboundDeliveryError.DeliveryNotFound(id)))
        case Some(receiving: InboundDelivery.Receiving) =>
          val (updated, event) = receiving.receive(quantity, rejectedQuantity, at)
          inboundDeliveryRepository
            .save(updated, event)
            .map(_ => Right(InboundDeliveryReceiveResult(updated, event)))
        case Some(newDel: InboundDelivery.New) =>
          val (receiving, startEvent) = newDel.startReceiving(at)
          inboundDeliveryRepository
            .save(receiving, startEvent)
            .flatMap { _ =>
              val (updated, recvEvent) =
                receiving.receive(quantity, rejectedQuantity, at)
              inboundDeliveryRepository
                .save(updated, recvEvent)
                .map(_ => Right(InboundDeliveryReceiveResult(updated, recvEvent)))
            }
        case Some(_) =>
          Future.successful(
            Left(InboundDeliveryError.DeliveryInWrongState(id))
          )

  def createReceipt(
      inboundDeliveryId: InboundDeliveryId,
      at: Instant
  ): Future[Either[InboundDeliveryError, GoodsReceiptCreateResult]] =
    val id = GoodsReceiptId()
    val receipt = GoodsReceipt.Open(id, inboundDeliveryId, List.empty)
    val event = GoodsReceiptEvent.GoodsReceiptCreated(id, inboundDeliveryId, at)
    goodsReceiptRepository
      .save(receipt, event)
      .map(_ => Right(GoodsReceiptCreateResult(receipt, event)))

  def recordLine(
      receiptId: GoodsReceiptId,
      line: ReceivedLine,
      at: Instant
  ): Future[Either[InboundDeliveryError, GoodsReceiptRecordLineResult]] =
    goodsReceiptRepository
      .findById(receiptId)
      .flatMap:
        case None =>
          Future.successful(
            Left(InboundDeliveryError.ReceiptNotFound(receiptId))
          )
        case Some(open: GoodsReceipt.Open) =>
          val (updated, event) = open.recordLine(line, at)
          goodsReceiptRepository
            .save(updated, event)
            .map(_ => Right(GoodsReceiptRecordLineResult(updated, event)))
        case Some(_) =>
          Future.successful(
            Left(InboundDeliveryError.ReceiptInWrongState(receiptId))
          )

  def confirmReceipt(
      receiptId: GoodsReceiptId,
      at: Instant
  ): Future[Either[InboundDeliveryError, GoodsReceiptConfirmResult]] =
    goodsReceiptRepository
      .findById(receiptId)
      .flatMap:
        case None =>
          Future.successful(
            Left(InboundDeliveryError.ReceiptNotFound(receiptId))
          )
        case Some(open: GoodsReceipt.Open) =>
          val (confirmed, event) = open.confirm(at)
          goodsReceiptRepository
            .save(confirmed, event)
            .map(_ => Right(GoodsReceiptConfirmResult(confirmed, event)))
        case Some(_) =>
          Future.successful(
            Left(InboundDeliveryError.ReceiptInWrongState(receiptId))
          )
