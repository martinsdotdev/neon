package neon.handlingunit

import com.typesafe.config.ConfigFactory
import neon.common.{HandlingUnitId, LocationId, OrderId, PackagingLevel}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class HandlingUnitActorSuite
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

  private val handlingUnitId = HandlingUnitId()
  private val locationId = LocationId()
  private val bufferLocationId = LocationId()
  private val orderId = OrderId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    HandlingUnitActor.Command,
    HandlingUnitActor.ActorEvent,
    HandlingUnitActor.State
  ](
    system,
    HandlingUnitActor(handlingUnitId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createPickUnit(): Unit =
    val pickCreated = HandlingUnit.PickCreated(
      handlingUnitId,
      PackagingLevel.Each,
      locationId
    )
    esTestKit.runCommand[StatusReply[Done]](
      HandlingUnitActor.Create(pickCreated, _)
    )

  private def createShipUnit(): Unit =
    val shipCreated = HandlingUnit.ShipCreated(
      handlingUnitId,
      PackagingLevel.Each,
      locationId,
      orderId
    )
    esTestKit.runCommand[StatusReply[Done]](
      HandlingUnitActor.Create(shipCreated, _)
    )

  describe("HandlingUnitActor"):
    describe("pick flow: PickCreated -> InBuffer -> Empty"):
      it("transitions through the pick stream"):
        createPickUnit()

        val moveResult =
          esTestKit.runCommand[StatusReply[
            HandlingUnitActor.MoveToBufferResponse
          ]](
            HandlingUnitActor.MoveToBuffer(
              bufferLocationId,
              at,
              _
            )
          )
        assert(moveResult.reply.isSuccess)
        assert(
          moveResult.reply.getValue.inBuffer
            .isInstanceOf[HandlingUnit.InBuffer]
        )

        val emptyResult =
          esTestKit.runCommand[StatusReply[
            HandlingUnitActor.EmptyResponse
          ]](
            HandlingUnitActor.Empty(at, _)
          )
        assert(emptyResult.reply.isSuccess)
        assert(
          emptyResult.reply.getValue.empty
            .isInstanceOf[HandlingUnit.Empty]
        )

    describe(
      "ship flow: ShipCreated -> Packed -> ReadyToShip -> Shipped"
    ):
      it("transitions through the ship stream"):
        createShipUnit()

        val packResult =
          esTestKit.runCommand[StatusReply[
            HandlingUnitActor.PackResponse
          ]](
            HandlingUnitActor.Pack(at, _)
          )
        assert(packResult.reply.isSuccess)

        val readyResult =
          esTestKit.runCommand[StatusReply[
            HandlingUnitActor.ReadyToShipResponse
          ]](
            HandlingUnitActor.ReadyToShip(at, _)
          )
        assert(readyResult.reply.isSuccess)

        val shipResult =
          esTestKit.runCommand[StatusReply[
            HandlingUnitActor.ShipResponse
          ]](
            HandlingUnitActor.Ship(at, _)
          )
        assert(shipResult.reply.isSuccess)
        assert(
          shipResult.reply.getValue.shipped
            .isInstanceOf[HandlingUnit.Shipped]
        )

    describe("cross-flow rejection"):
      it("rejects Pack on PickCreated"):
        createPickUnit()
        val result =
          esTestKit.runCommand[StatusReply[
            HandlingUnitActor.PackResponse
          ]](
            HandlingUnitActor.Pack(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects MoveToBuffer on ShipCreated"):
        createShipUnit()
        val result =
          esTestKit.runCommand[StatusReply[
            HandlingUnitActor.MoveToBufferResponse
          ]](
            HandlingUnitActor.MoveToBuffer(
              bufferLocationId,
              at,
              _
            )
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Ship on Packed"):
        createShipUnit()
        esTestKit.runCommand[StatusReply[
          HandlingUnitActor.PackResponse
        ]](
          HandlingUnitActor.Pack(at, _)
        )

        val result =
          esTestKit.runCommand[StatusReply[
            HandlingUnitActor.ShipResponse
          ]](
            HandlingUnitActor.Ship(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("idempotent Create"):
      it("acks on second Create without error"):
        createPickUnit()
        val pickCreated = HandlingUnit.PickCreated(
          handlingUnitId,
          PackagingLevel.Each,
          locationId
        )
        val result = esTestKit.runCommand[StatusReply[Done]](
          HandlingUnitActor.Create(pickCreated, _)
        )
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers InBuffer state from journal"):
        createPickUnit()
        esTestKit.runCommand[StatusReply[
          HandlingUnitActor.MoveToBufferResponse
        ]](
          HandlingUnitActor.MoveToBuffer(bufferLocationId, at, _)
        )
        esTestKit.restart()

        val result =
          esTestKit.runCommand[Option[HandlingUnit]](
            HandlingUnitActor.GetState(_)
          )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[HandlingUnit.InBuffer])
        val inBuffer =
          result.reply.get.asInstanceOf[HandlingUnit.InBuffer]
        assert(inBuffer.currentLocation == bufferLocationId)

      it("recovers Packed state from journal"):
        createShipUnit()
        esTestKit.runCommand[StatusReply[
          HandlingUnitActor.PackResponse
        ]](
          HandlingUnitActor.Pack(at, _)
        )
        esTestKit.restart()

        val result =
          esTestKit.runCommand[Option[HandlingUnit]](
            HandlingUnitActor.GetState(_)
          )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[HandlingUnit.Packed])
