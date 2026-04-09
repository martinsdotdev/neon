package neon.slot

import com.typesafe.config.ConfigFactory
import neon.common.{HandlingUnitId, OrderId, SlotId, WorkstationId}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class SlotActorSuite
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

  private val slotId = SlotId()
  private val workstationId = WorkstationId()
  private val orderId = OrderId()
  private val handlingUnitId = HandlingUnitId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    SlotActor.Command,
    SlotActor.ActorEvent,
    SlotActor.State
  ](
    system,
    SlotActor(slotId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createAvailable(): Unit =
    val slot = Slot.Available(slotId, workstationId)
    esTestKit.runCommand[StatusReply[Done]](
      SlotActor.Create(slot, _)
    )

  private def reserveSlot(): Unit =
    esTestKit.runCommand[StatusReply[SlotActor.ReserveResponse]](
      SlotActor.Reserve(orderId, handlingUnitId, at, _)
    )

  describe("SlotActor"):
    describe("lifecycle Available -> Reserved -> Completed"):
      it("transitions through all states"):
        createAvailable()
        reserveSlot()

        val result =
          esTestKit.runCommand[StatusReply[
            SlotActor.CompleteResponse
          ]](
            SlotActor.Complete(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.completed
            .isInstanceOf[Slot.Completed]
        )

    describe("Release from Reserved"):
      it("transitions back to Available"):
        createAvailable()
        reserveSlot()

        val result =
          esTestKit.runCommand[StatusReply[
            SlotActor.ReleaseResponse
          ]](
            SlotActor.Release(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.available
            .isInstanceOf[Slot.Available]
        )

      it("allows re-reservation after release"):
        createAvailable()
        reserveSlot()
        esTestKit.runCommand[StatusReply[
          SlotActor.ReleaseResponse
        ]](
          SlotActor.Release(at, _)
        )

        val newOrderId = OrderId()
        val result =
          esTestKit.runCommand[StatusReply[
            SlotActor.ReserveResponse
          ]](
            SlotActor.Reserve(newOrderId, HandlingUnitId(), at, _)
          )
        assert(result.reply.isSuccess)
        assert(result.reply.getValue.reserved.orderId == newOrderId)

    describe("idempotent Create"):
      it("acks on second Create without error"):
        createAvailable()
        val slot = Slot.Available(slotId, workstationId)
        val result = esTestKit.runCommand[StatusReply[Done]](
          SlotActor.Create(slot, _)
        )
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)

    describe("invalid transitions"):
      it("rejects Reserve on Reserved"):
        createAvailable()
        reserveSlot()
        val result =
          esTestKit.runCommand[StatusReply[
            SlotActor.ReserveResponse
          ]](
            SlotActor.Reserve(OrderId(), HandlingUnitId(), at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Complete on Available"):
        createAvailable()
        val result =
          esTestKit.runCommand[StatusReply[
            SlotActor.CompleteResponse
          ]](
            SlotActor.Complete(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Release on Available"):
        createAvailable()
        val result =
          esTestKit.runCommand[StatusReply[
            SlotActor.ReleaseResponse
          ]](
            SlotActor.Release(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers Reserved state from journal"):
        createAvailable()
        reserveSlot()
        esTestKit.restart()

        val result =
          esTestKit.runCommand[Option[Slot]](
            SlotActor.GetState(_)
          )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[Slot.Reserved])
        val reserved = result.reply.get.asInstanceOf[Slot.Reserved]
        assert(reserved.orderId == orderId)
        assert(reserved.handlingUnitId == handlingUnitId)
