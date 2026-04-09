package neon.goodsreceipt

import neon.common.GoodsReceiptId

import scala.concurrent.Future

/** Async port trait for [[GoodsReceipt]] aggregate persistence and queries. */
trait AsyncGoodsReceiptRepository:
  def findById(id: GoodsReceiptId): Future[Option[GoodsReceipt]]
  def save(receipt: GoodsReceipt, event: GoodsReceiptEvent): Future[Unit]
