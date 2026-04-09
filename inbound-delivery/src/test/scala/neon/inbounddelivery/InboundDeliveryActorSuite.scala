package neon.inbounddelivery

import com.typesafe.config.ConfigFactory
import neon.common.{InboundDeliveryId, LotAttributes, PackagingLevel, SkuId}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class InboundDeliveryActorSuite
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

  private val deliveryId = InboundDeliveryId()
  private val skuId = SkuId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    InboundDeliveryActor.Command,
    InboundDeliveryEvent,
    InboundDeliveryActor.State
  ](
    system,
    InboundDeliveryActor(deliveryId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createDelivery(): Unit =
    val delivery =
      InboundDelivery.New(deliveryId, skuId, PackagingLevel.Each, LotAttributes(), 100)
    val event = InboundDeliveryEvent.InboundDeliveryCreated(
      deliveryId,
      skuId,
      PackagingLevel.Each,
      LotAttributes(),
      100,
      at
    )
    esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
      InboundDeliveryActor.Create(delivery, event, _)
    )

  private def startReceiving(): Unit =
    esTestKit.runCommand[StatusReply[InboundDeliveryActor.StartReceivingResponse]](
      InboundDeliveryActor.StartReceiving(at, _)
    )

  describe("InboundDeliveryActor"):
    describe("Create"):
      it("persists InboundDeliveryCreated event and sets New state"):
        val delivery =
          InboundDelivery.New(deliveryId, skuId, PackagingLevel.Each, LotAttributes(), 100)
        val event = InboundDeliveryEvent.InboundDeliveryCreated(
          deliveryId,
          skuId,
          PackagingLevel.Each,
          LotAttributes(),
          100,
          at
        )
        val result = esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
          InboundDeliveryActor.Create(delivery, event, _)
        )
        assert(result.event == event)
        assert(
          result
            .stateOfType[InboundDeliveryActor.ActiveState]
            .delivery
            .isInstanceOf[InboundDelivery.New]
        )

    describe("GetState"):
      it("returns None when empty"):
        val result = esTestKit.runCommand[Option[InboundDelivery]](
          InboundDeliveryActor.GetState(_)
        )
        assert(result.reply.isEmpty)

      it("returns Some after creation"):
        createDelivery()
        val result = esTestKit.runCommand[Option[InboundDelivery]](
          InboundDeliveryActor.GetState(_)
        )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[InboundDelivery.New])

    describe("StartReceiving"):
      it("transitions New to Receiving"):
        createDelivery()
        val result =
          esTestKit.runCommand[StatusReply[InboundDeliveryActor.StartReceivingResponse]](
            InboundDeliveryActor.StartReceiving(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result
            .stateOfType[InboundDeliveryActor.ActiveState]
            .delivery
            .isInstanceOf[InboundDelivery.Receiving]
        )

      it("rejects StartReceiving in EmptyState"):
        val result =
          esTestKit.runCommand[StatusReply[InboundDeliveryActor.StartReceivingResponse]](
            InboundDeliveryActor.StartReceiving(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Receive"):
      it("increments received quantity"):
        createDelivery()
        startReceiving()
        val result =
          esTestKit.runCommand[StatusReply[InboundDeliveryActor.ReceiveResponse]](
            InboundDeliveryActor.Receive(10, 0, at, _)
          )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.delivery.asInstanceOf[InboundDelivery.Receiving].receivedQuantity == 10)

      it("rejects Receive when not in Receiving state"):
        createDelivery()
        val result =
          esTestKit.runCommand[StatusReply[InboundDeliveryActor.ReceiveResponse]](
            InboundDeliveryActor.Receive(10, 0, at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Complete"):
      it("transitions Receiving to Received when fully received"):
        createDelivery()
        startReceiving()
        esTestKit.runCommand[StatusReply[InboundDeliveryActor.ReceiveResponse]](
          InboundDeliveryActor.Receive(90, 10, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[InboundDeliveryActor.CompleteResponse]](
            InboundDeliveryActor.Complete(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result
            .stateOfType[InboundDeliveryActor.ActiveState]
            .delivery
            .isInstanceOf[InboundDelivery.Received]
        )

    describe("Close"):
      it("forces remaining as rejected and transitions to Closed"):
        createDelivery()
        startReceiving()
        esTestKit.runCommand[StatusReply[InboundDeliveryActor.ReceiveResponse]](
          InboundDeliveryActor.Receive(50, 10, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[InboundDeliveryActor.CloseResponse]](
            InboundDeliveryActor.Close(at, _)
          )
        assert(result.reply.isSuccess)
        val closed = result
          .stateOfType[InboundDeliveryActor.ActiveState]
          .delivery
          .asInstanceOf[InboundDelivery.Closed]
        assert(closed.receivedQuantity == 50)
        assert(closed.rejectedQuantity == 50)

    describe("Cancel"):
      it("cancels a New delivery"):
        createDelivery()
        val result =
          esTestKit.runCommand[StatusReply[InboundDeliveryActor.CancelResponse]](
            InboundDeliveryActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result
            .stateOfType[InboundDeliveryActor.ActiveState]
            .delivery
            .isInstanceOf[InboundDelivery.Cancelled]
        )

      it("rejects Cancel on Receiving delivery"):
        createDelivery()
        startReceiving()
        val result =
          esTestKit.runCommand[StatusReply[InboundDeliveryActor.CancelResponse]](
            InboundDeliveryActor.Cancel(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers New state from journal"):
        createDelivery()
        esTestKit.restart()
        val result = esTestKit.runCommand[Option[InboundDelivery]](
          InboundDeliveryActor.GetState(_)
        )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[InboundDelivery.New])
