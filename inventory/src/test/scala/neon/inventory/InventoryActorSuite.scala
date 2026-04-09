package neon.inventory

import com.typesafe.config.ConfigFactory
import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, SkuId}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class InventoryActorSuite
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

  private val inventoryId = InventoryId()
  private val locationId = LocationId()
  private val skuId = SkuId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    InventoryActor.Command,
    InventoryEvent,
    InventoryActor.State
  ](
    system,
    InventoryActor(inventoryId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createInventory(onHand: Int = 100): Unit =
    val (inventory, event) = Inventory.create(
      locationId,
      skuId,
      PackagingLevel.Each,
      Some(Lot("LOT-001")),
      onHand,
      at
    )
    esTestKit.runCommand[StatusReply[Done]](
      InventoryActor.Create(inventory, event, _)
    )

  describe("InventoryActor"):
    describe("Reserve"):
      it("reserves quantity from available stock"):
        createInventory(onHand = 100)
        val result =
          esTestKit.runCommand[StatusReply[
            InventoryActor.MutationResponse
          ]](
            InventoryActor.Reserve(20, at, _)
          )
        assert(result.reply.isSuccess)
        val inventory = result.reply.getValue.inventory
        assert(inventory.reserved == 20)
        assert(inventory.available == 80)

    describe("Release"):
      it("releases reserved quantity back to available"):
        createInventory(onHand = 100)
        esTestKit.runCommand[StatusReply[
          InventoryActor.MutationResponse
        ]](
          InventoryActor.Reserve(30, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[
            InventoryActor.MutationResponse
          ]](
            InventoryActor.Release(10, at, _)
          )
        assert(result.reply.isSuccess)
        val inventory = result.reply.getValue.inventory
        assert(inventory.reserved == 20)
        assert(inventory.available == 80)

    describe("Consume"):
      it("reduces both on-hand and reserved"):
        createInventory(onHand = 100)
        esTestKit.runCommand[StatusReply[
          InventoryActor.MutationResponse
        ]](
          InventoryActor.Reserve(30, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[
            InventoryActor.MutationResponse
          ]](
            InventoryActor.Consume(20, at, _)
          )
        assert(result.reply.isSuccess)
        val inventory = result.reply.getValue.inventory
        assert(inventory.onHand == 80)
        assert(inventory.reserved == 10)

    describe("CorrectLot"):
      it("corrects lot when reserved is zero"):
        createInventory(onHand = 50)
        val result =
          esTestKit.runCommand[StatusReply[
            InventoryActor.MutationResponse
          ]](
            InventoryActor.CorrectLot(Some(Lot("LOT-002")), at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.inventory.lot
            .contains(Lot("LOT-002"))
        )

    describe("commands on empty state"):
      it("rejects Reserve on empty state"):
        val result =
          esTestKit.runCommand[StatusReply[
            InventoryActor.MutationResponse
          ]](
            InventoryActor.Reserve(10, at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("GetState returns None on empty state"):
        val result =
          esTestKit.runCommand[Option[Inventory]](
            InventoryActor.GetState(_)
          )
        assert(result.reply.isEmpty)

    describe("event replay"):
      it("recovers inventory with reserved quantity"):
        createInventory(onHand = 100)
        esTestKit.runCommand[StatusReply[
          InventoryActor.MutationResponse
        ]](
          InventoryActor.Reserve(25, at, _)
        )
        esTestKit.restart()

        val result =
          esTestKit.runCommand[Option[Inventory]](
            InventoryActor.GetState(_)
          )
        assert(result.reply.isDefined)
        val inventory = result.reply.get
        assert(inventory.onHand == 100)
        assert(inventory.reserved == 25)
        assert(inventory.available == 75)
