package neon.goodsreceipt

import com.typesafe.config.ConfigFactory
import neon.common.{
  ContainerId,
  GoodsReceiptId,
  InboundDeliveryId,
  LotAttributes,
  PackagingLevel,
  SkuId
}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class GoodsReceiptActorSuite
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString("""
          pekko.actor {
            provider = local
            serialization-bindings {
              "neon.common.serialization.CborSerializable" = jackson-cbor
            }
          }
        """)
        .withFallback(EventSourcedBehaviorTestKit.config)
        .resolve()
    )
    with AnyFunSpecLike
    with BeforeAndAfterEach:

  private val receiptId = GoodsReceiptId()
  private val inboundDeliveryId = InboundDeliveryId()
  private val skuId = SkuId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    GoodsReceiptActor.Command,
    GoodsReceiptEvent,
    GoodsReceiptActor.State
  ](
    system,
    GoodsReceiptActor(receiptId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createReceipt(): Unit =
    val receipt = GoodsReceipt.Open(receiptId, inboundDeliveryId, List.empty)
    val event = GoodsReceiptEvent.GoodsReceiptCreated(
      receiptId,
      inboundDeliveryId,
      at
    )
    esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
      GoodsReceiptActor.Create(receipt, event, _)
    )

  private def recordLine(): Unit =
    val line = ReceivedLine(skuId, 10, PackagingLevel.Each, LotAttributes(), None)
    esTestKit.runCommand[StatusReply[GoodsReceiptActor.RecordLineResponse]](
      GoodsReceiptActor.RecordLine(line, at, _)
    )

  describe("GoodsReceiptActor"):
    describe("Create"):
      it("persists GoodsReceiptCreated event and sets Open state"):
        val receipt = GoodsReceipt.Open(receiptId, inboundDeliveryId, List.empty)
        val event = GoodsReceiptEvent.GoodsReceiptCreated(
          receiptId,
          inboundDeliveryId,
          at
        )
        val result = esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
          GoodsReceiptActor.Create(receipt, event, _)
        )
        assert(result.event == event)
        assert(
          result
            .stateOfType[GoodsReceiptActor.ActiveState]
            .receipt
            .isInstanceOf[GoodsReceipt.Open]
        )

    describe("GetState"):
      it("returns None when empty"):
        val result = esTestKit.runCommand[Option[GoodsReceipt]](
          GoodsReceiptActor.GetState(_)
        )
        assert(result.reply.isEmpty)

      it("returns Some after creation"):
        createReceipt()
        val result = esTestKit.runCommand[Option[GoodsReceipt]](
          GoodsReceiptActor.GetState(_)
        )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[GoodsReceipt.Open])

    describe("RecordLine"):
      it("adds a line to the receipt"):
        createReceipt()
        val line =
          ReceivedLine(skuId, 10, PackagingLevel.Each, LotAttributes(), None)
        val result =
          esTestKit.runCommand[StatusReply[GoodsReceiptActor.RecordLineResponse]](
            GoodsReceiptActor.RecordLine(line, at, _)
          )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.receipt.receivedLines.size == 1)

      it("rejects RecordLine when not Open"):
        val result =
          esTestKit.runCommand[StatusReply[GoodsReceiptActor.RecordLineResponse]](
            GoodsReceiptActor.RecordLine(
              ReceivedLine(skuId, 10, PackagingLevel.Each, LotAttributes(), None),
              at,
              _
            )
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Confirm"):
      it("transitions Open to Confirmed"):
        createReceipt()
        recordLine()
        val result =
          esTestKit.runCommand[StatusReply[GoodsReceiptActor.ConfirmResponse]](
            GoodsReceiptActor.Confirm(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result
            .stateOfType[GoodsReceiptActor.ActiveState]
            .receipt
            .isInstanceOf[GoodsReceipt.Confirmed]
        )

      it("rejects Confirm when no lines recorded"):
        createReceipt()
        val result =
          esTestKit.runCommand[StatusReply[GoodsReceiptActor.ConfirmResponse]](
            GoodsReceiptActor.Confirm(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Cancel"):
      it("cancels an Open receipt"):
        createReceipt()
        val result =
          esTestKit.runCommand[StatusReply[GoodsReceiptActor.CancelResponse]](
            GoodsReceiptActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result
            .stateOfType[GoodsReceiptActor.ActiveState]
            .receipt
            .isInstanceOf[GoodsReceipt.Cancelled]
        )

      it("rejects Cancel on Confirmed receipt"):
        createReceipt()
        recordLine()
        esTestKit.runCommand[StatusReply[GoodsReceiptActor.ConfirmResponse]](
          GoodsReceiptActor.Confirm(at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[GoodsReceiptActor.CancelResponse]](
            GoodsReceiptActor.Cancel(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers Open state from journal"):
        createReceipt()
        esTestKit.restart()
        val result = esTestKit.runCommand[Option[GoodsReceipt]](
          GoodsReceiptActor.GetState(_)
        )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[GoodsReceipt.Open])
