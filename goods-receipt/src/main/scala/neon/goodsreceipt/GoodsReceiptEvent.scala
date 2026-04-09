package neon.goodsreceipt

import neon.common.serialization.CborSerializable
import neon.common.{GoodsReceiptId, InboundDeliveryId}

import java.time.Instant

/** Domain events emitted by [[GoodsReceipt]] state transitions. */
sealed trait GoodsReceiptEvent extends CborSerializable:
  /** The goods receipt that emitted this event. */
  def goodsReceiptId: GoodsReceiptId

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for the [[GoodsReceipt]] aggregate. */
object GoodsReceiptEvent:

  /** Emitted when a new goods receipt is created. */
  case class GoodsReceiptCreated(
      goodsReceiptId: GoodsReceiptId,
      inboundDeliveryId: InboundDeliveryId,
      occurredAt: Instant
  ) extends GoodsReceiptEvent

  /** Emitted when a received line is recorded on a goods receipt. */
  case class LineRecorded(
      goodsReceiptId: GoodsReceiptId,
      line: ReceivedLine,
      occurredAt: Instant
  ) extends GoodsReceiptEvent

  /** Emitted when a goods receipt is confirmed. */
  case class GoodsReceiptConfirmed(
      goodsReceiptId: GoodsReceiptId,
      inboundDeliveryId: InboundDeliveryId,
      receivedLines: List[ReceivedLine],
      occurredAt: Instant
  ) extends GoodsReceiptEvent

  /** Emitted when a goods receipt is cancelled. */
  case class GoodsReceiptCancelled(
      goodsReceiptId: GoodsReceiptId,
      occurredAt: Instant
  ) extends GoodsReceiptEvent
