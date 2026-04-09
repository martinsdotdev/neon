package neon.goodsreceipt

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{GoodsReceiptId, InboundDeliveryId}

import java.time.Instant

/** Typestate-encoded aggregate for goods receipt lifecycle management.
  *
  * A goods receipt represents a physical receiving session against an inbound delivery. The state
  * machine follows: [[GoodsReceipt.Open]] -> [[GoodsReceipt.Confirmed]] |
  * [[GoodsReceipt.Cancelled]]. Transitions are only available on valid source states, enforced at
  * compile time.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait GoodsReceipt:
  /** The unique identifier of this goods receipt. */
  def id: GoodsReceiptId

  /** The inbound delivery this receipt is associated with. */
  def inboundDeliveryId: InboundDeliveryId

  /** The lines received in this receipt. */
  def receivedLines: List[ReceivedLine]

/** Factory and state definitions for the [[GoodsReceipt]] aggregate. */
object GoodsReceipt:

  /** A goods receipt that is open for recording received lines.
    *
    * Transitions: [[recordLine]] -> [[Open]], [[confirm]] -> [[Confirmed]], [[cancel]] ->
    * [[Cancelled]].
    */
  case class Open(
      id: GoodsReceiptId,
      inboundDeliveryId: InboundDeliveryId,
      receivedLines: List[ReceivedLine]
  ) extends GoodsReceipt:

    /** Records a received line item in this receipt.
      *
      * @param line
      *   the received line to record
      * @param at
      *   instant of the recording
      * @return
      *   updated open receipt and event
      */
    def recordLine(
        line: ReceivedLine,
        at: Instant
    ): (Open, GoodsReceiptEvent.LineRecorded) =
      require(line.quantity > 0, s"line quantity must be positive, got ${line.quantity}")
      val updated = copy(receivedLines = receivedLines :+ line)
      val event = GoodsReceiptEvent.LineRecorded(id, line, at)
      (updated, event)

    /** Confirms the goods receipt, finalizing all recorded lines.
      *
      * @param at
      *   instant of the confirmation
      * @return
      *   confirmed receipt and event
      */
    def confirm(at: Instant): (Confirmed, GoodsReceiptEvent.GoodsReceiptConfirmed) =
      require(receivedLines.nonEmpty, "cannot confirm receipt with no lines")
      val confirmed = Confirmed(id, inboundDeliveryId, receivedLines)
      val event = GoodsReceiptEvent.GoodsReceiptConfirmed(id, inboundDeliveryId, receivedLines, at)
      (confirmed, event)

    /** Cancels this open goods receipt.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled receipt and event
      */
    def cancel(at: Instant): (Cancelled, GoodsReceiptEvent.GoodsReceiptCancelled) =
      val cancelled = Cancelled(id, inboundDeliveryId, receivedLines)
      val event = GoodsReceiptEvent.GoodsReceiptCancelled(id, at)
      (cancelled, event)

  /** A goods receipt that has been confirmed. Terminal state. */
  case class Confirmed(
      id: GoodsReceiptId,
      inboundDeliveryId: InboundDeliveryId,
      receivedLines: List[ReceivedLine]
  ) extends GoodsReceipt

  /** A goods receipt that was cancelled. Terminal state. */
  case class Cancelled(
      id: GoodsReceiptId,
      inboundDeliveryId: InboundDeliveryId,
      receivedLines: List[ReceivedLine]
  ) extends GoodsReceipt
