package neon.inbounddelivery

import neon.common.{InboundDeliveryId, LotAttributes, PackagingLevel, SkuId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class InboundDeliverySuite extends AnyFunSpec:
  val id = InboundDeliveryId()
  val skuId = SkuId()
  val packagingLevel = PackagingLevel.Each
  val lotAttributes = LotAttributes()
  val at = Instant.now()

  def newDelivery(expectedQuantity: Int = 100): InboundDelivery.New =
    InboundDelivery.New(id, skuId, packagingLevel, lotAttributes, expectedQuantity)

  describe("InboundDelivery"):
    describe("creation"):
      it("validates expectedQuantity is positive"):
        intercept[IllegalArgumentException]:
          InboundDelivery.New(id, skuId, packagingLevel, lotAttributes, 0)

      it("validates expectedQuantity is not negative"):
        intercept[IllegalArgumentException]:
          InboundDelivery.New(id, skuId, packagingLevel, lotAttributes, -1)

    describe("starting to receive"):
      it("transitions from New to Receiving"):
        val (receiving, event) = newDelivery().startReceiving(at)
        assert(receiving.isInstanceOf[InboundDelivery.Receiving])
        assert(receiving.receivedQuantity == 0)
        assert(receiving.rejectedQuantity == 0)

      it("emits ReceivingStarted event"):
        val (_, event) = newDelivery().startReceiving(at)
        assert(event.inboundDeliveryId == id)
        assert(event.occurredAt == at)

    describe("receiving quantity"):
      it("increments received quantity"):
        val (receiving, _) = newDelivery().startReceiving(at)
        val (updated, event) = receiving.receive(10, 0, at)
        assert(updated.receivedQuantity == 10)
        assert(updated.rejectedQuantity == 0)

      it("increments rejected quantity"):
        val (receiving, _) = newDelivery().startReceiving(at)
        val (updated, _) = receiving.receive(5, 3, at)
        assert(updated.receivedQuantity == 5)
        assert(updated.rejectedQuantity == 3)

      it("accumulates across multiple receives"):
        val (receiving, _) = newDelivery().startReceiving(at)
        val (after1, _) = receiving.receive(30, 5, at)
        val (after2, _) = after1.receive(20, 10, at)
        assert(after2.receivedQuantity == 50)
        assert(after2.rejectedQuantity == 15)

      it("emits QuantityReceived event with correct quantities"):
        val (receiving, _) = newDelivery().startReceiving(at)
        val (_, event) = receiving.receive(10, 2, at)
        assert(event.inboundDeliveryId == id)
        assert(event.quantity == 10)
        assert(event.rejectedQuantity == 2)
        assert(event.occurredAt == at)

      it("rejects when received + rejected exceeds expected"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (after1, _) = receiving.receive(10, 5, at)
        intercept[IllegalArgumentException]:
          after1.receive(4, 2, at)

      it("allows receiving up to exactly expected quantity"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (updated, _) = receiving.receive(15, 5, at)
        assert(updated.receivedQuantity == 15)
        assert(updated.rejectedQuantity == 5)

      it("rejects zero quantity when rejected is also zero"):
        val (receiving, _) = newDelivery().startReceiving(at)
        intercept[IllegalArgumentException]:
          receiving.receive(0, 0, at)

    describe("isFullyReceived"):
      it("returns true when received + rejected equals expected"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (updated, _) = receiving.receive(15, 5, at)
        assert(updated.isFullyReceived)

      it("returns false when quantities do not match expected"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (updated, _) = receiving.receive(10, 0, at)
        assert(!updated.isFullyReceived)

    describe("completing"):
      it("transitions to Received when fully received"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (updated, _) = receiving.receive(15, 5, at)
        val (received, event) = updated.complete(at)
        assert(received.isInstanceOf[InboundDelivery.Received])
        assert(received.receivedQuantity == 15)
        assert(received.rejectedQuantity == 5)

      it("emits Received event"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (updated, _) = receiving.receive(15, 5, at)
        val (_, event) = updated.complete(at)
        assert(event.inboundDeliveryId == id)
        assert(event.occurredAt == at)

      it("rejects completion when not fully received"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (updated, _) = receiving.receive(10, 0, at)
        intercept[IllegalArgumentException]:
          updated.complete(at)

    describe("closing"):
      it("forces remaining quantity as rejected"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (updated, _) = receiving.receive(10, 2, at)
        val (closed, event) = updated.close(at)
        assert(closed.isInstanceOf[InboundDelivery.Closed])
        assert(closed.receivedQuantity == 10)
        assert(closed.rejectedQuantity == 10)

      it("emits Closed event"):
        val (receiving, _) = newDelivery(expectedQuantity = 20).startReceiving(at)
        val (updated, _) = receiving.receive(10, 2, at)
        val (_, event) = updated.close(at)
        assert(event.inboundDeliveryId == id)
        assert(event.occurredAt == at)

    describe("cancelling"):
      it("transitions from New to Cancelled"):
        val (cancelled, event) = newDelivery().cancel(at)
        assert(cancelled.isInstanceOf[InboundDelivery.Cancelled])
        assert(event.inboundDeliveryId == id)
        assert(event.occurredAt == at)
