package neon.transportorder

import com.typesafe.config.ConfigFactory
import neon.common.{HandlingUnitId, LocationId, TransportOrderId}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class TransportOrderActorSuite
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

  private val transportOrderId = TransportOrderId()
  private val handlingUnitId = HandlingUnitId()
  private val destination = LocationId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    TransportOrderActor.Command,
    TransportOrderEvent,
    TransportOrderActor.State
  ](
    system,
    TransportOrderActor(transportOrderId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createPending(): Unit =
    val (pending, event) =
      TransportOrder.create(handlingUnitId, destination, at)
    esTestKit.runCommand[StatusReply[Done]](
      TransportOrderActor.Create(pending, event, _)
    )

  describe("TransportOrderActor"):
    describe("full lifecycle Pending -> Confirmed"):
      it("transitions through confirm"):
        createPending()

        val result =
          esTestKit.runCommand[StatusReply[
            TransportOrderActor.ConfirmResponse
          ]](
            TransportOrderActor.Confirm(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.confirmed
            .isInstanceOf[TransportOrder.Confirmed]
        )

    describe("full lifecycle Pending -> Cancelled"):
      it("transitions through cancel"):
        createPending()

        val result =
          esTestKit.runCommand[StatusReply[
            TransportOrderActor.CancelResponse
          ]](
            TransportOrderActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.cancelled
            .isInstanceOf[TransportOrder.Cancelled]
        )

    describe("invalid transitions"):
      it("rejects Confirm on Cancelled order"):
        createPending()
        esTestKit.runCommand[StatusReply[
          TransportOrderActor.CancelResponse
        ]](
          TransportOrderActor.Cancel(at, _)
        )

        val result =
          esTestKit.runCommand[StatusReply[
            TransportOrderActor.ConfirmResponse
          ]](
            TransportOrderActor.Confirm(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Cancel on Confirmed order"):
        createPending()
        esTestKit.runCommand[StatusReply[
          TransportOrderActor.ConfirmResponse
        ]](
          TransportOrderActor.Confirm(at, _)
        )

        val result =
          esTestKit.runCommand[StatusReply[
            TransportOrderActor.CancelResponse
          ]](
            TransportOrderActor.Cancel(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Confirm on empty state"):
        val result =
          esTestKit.runCommand[StatusReply[
            TransportOrderActor.ConfirmResponse
          ]](
            TransportOrderActor.Confirm(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("GetState"):
      it("returns None on empty state"):
        val result =
          esTestKit.runCommand[Option[TransportOrder]](
            TransportOrderActor.GetState(_)
          )
        assert(result.reply.isEmpty)

      it("returns Some after creation"):
        createPending()
        val result =
          esTestKit.runCommand[Option[TransportOrder]](
            TransportOrderActor.GetState(_)
          )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[TransportOrder.Pending])

    describe("event replay"):
      it("recovers Pending state from journal"):
        createPending()
        esTestKit.restart()

        val result =
          esTestKit.runCommand[Option[TransportOrder]](
            TransportOrderActor.GetState(_)
          )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[TransportOrder.Pending])
        assert(result.reply.get.handlingUnitId == handlingUnitId)
        assert(result.reply.get.destination == destination)

      it("recovers Confirmed state from journal"):
        createPending()
        esTestKit.runCommand[StatusReply[
          TransportOrderActor.ConfirmResponse
        ]](
          TransportOrderActor.Confirm(at, _)
        )
        esTestKit.restart()

        val result =
          esTestKit.runCommand[Option[TransportOrder]](
            TransportOrderActor.GetState(_)
          )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[TransportOrder.Confirmed])
