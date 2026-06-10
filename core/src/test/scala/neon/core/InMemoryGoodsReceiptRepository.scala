package neon.core

import neon.common.GoodsReceiptId
import neon.goodsreceipt.{GoodsReceipt, GoodsReceiptEvent, GoodsReceiptRepository}

import scala.collection.mutable

class InMemoryGoodsReceiptRepository extends GoodsReceiptRepository:
  val store: mutable.Map[GoodsReceiptId, GoodsReceipt] = mutable.Map.empty
  val events: mutable.ListBuffer[GoodsReceiptEvent] = mutable.ListBuffer.empty
  def findById(id: GoodsReceiptId): Option[GoodsReceipt] = store.get(id)
  def save(receipt: GoodsReceipt, event: GoodsReceiptEvent): Unit =
    store(receipt.id) = receipt
    events += event
