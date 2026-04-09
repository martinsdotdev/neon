package neon.goodsreceipt

import neon.common.{
  ContainerId,
  GoodsReceiptId,
  InboundDeliveryId,
  LotAttributes,
  PackagingLevel,
  SkuId
}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class GoodsReceiptSuite extends AnyFunSpec:
  val id = GoodsReceiptId()
  val inboundDeliveryId = InboundDeliveryId()
  val skuId = SkuId()
  val at = Instant.now()

  def openReceipt(): GoodsReceipt.Open =
    GoodsReceipt.Open(id, inboundDeliveryId, List.empty)

  def receivedLine(
      quantity: Int = 10,
      targetContainerId: Option[ContainerId] = None
  ): ReceivedLine =
    ReceivedLine(skuId, quantity, PackagingLevel.Each, LotAttributes(), targetContainerId)

  describe("GoodsReceipt"):
    describe("recording a line"):
      it("adds a received line to the receipt"):
        val (updated, event) = openReceipt().recordLine(receivedLine(), at)
        assert(updated.receivedLines.size == 1)
        assert(updated.receivedLines.head.skuId == skuId)

      it("emits LineRecorded event"):
        val line = receivedLine()
        val (_, event) = openReceipt().recordLine(line, at)
        assert(event.goodsReceiptId == id)
        assert(event.line == line)
        assert(event.occurredAt == at)

      it("accumulates multiple lines"):
        val (after1, _) = openReceipt().recordLine(receivedLine(5), at)
        val skuId2 = SkuId()
        val line2 =
          ReceivedLine(skuId2, 3, PackagingLevel.Case, LotAttributes(), None)
        val (after2, _) = after1.recordLine(line2, at)
        assert(after2.receivedLines.size == 2)

      it("validates quantity is positive"):
        intercept[IllegalArgumentException]:
          openReceipt().recordLine(receivedLine(0), at)

      it("supports optional target container"):
        val containerId = ContainerId()
        val line = receivedLine(targetContainerId = Some(containerId))
        val (updated, _) = openReceipt().recordLine(line, at)
        assert(updated.receivedLines.head.targetContainerId.contains(containerId))

    describe("confirming"):
      it("transitions to Confirmed"):
        val (withLine, _) = openReceipt().recordLine(receivedLine(), at)
        val (confirmed, event) = withLine.confirm(at)
        assert(confirmed.isInstanceOf[GoodsReceipt.Confirmed])
        assert(confirmed.receivedLines.size == 1)

      it("emits Confirmed event"):
        val (withLine, _) = openReceipt().recordLine(receivedLine(), at)
        val (_, event) = withLine.confirm(at)
        assert(event.goodsReceiptId == id)
        assert(event.occurredAt == at)

      it("rejects confirmation with no lines"):
        intercept[IllegalArgumentException]:
          openReceipt().confirm(at)

    describe("cancelling"):
      it("transitions to Cancelled"):
        val (cancelled, event) = openReceipt().cancel(at)
        assert(cancelled.isInstanceOf[GoodsReceipt.Cancelled])
        assert(event.goodsReceiptId == id)
        assert(event.occurredAt == at)

      it("can cancel after recording lines"):
        val (withLine, _) = openReceipt().recordLine(receivedLine(), at)
        val (cancelled, _) = withLine.cancel(at)
        assert(cancelled.isInstanceOf[GoodsReceipt.Cancelled])
