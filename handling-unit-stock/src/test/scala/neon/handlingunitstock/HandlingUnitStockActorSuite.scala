package neon.handlingunit

import com.typesafe.config.ConfigFactory
import neon.common.{
  AdjustmentReasonCode,
  ContainerId,
  HandlingUnitStockId,
  InventoryStatus,
  SlotCode,
  StockLockType,
  StockPositionId
}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class HandlingUnitStockActorSuite
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

  private val handlingUnitStockId = HandlingUnitStockId()
  private val containerId = ContainerId()
  private val slotCode = SlotCode("A-01")
  private val stockPositionId = StockPositionId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    HandlingUnitStockActor.Command,
    HandlingUnitStockEvent,
    HandlingUnitStockActor.State
  ](
    system,
    HandlingUnitStockActor(handlingUnitStockId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createHandlingUnitStock(onHand: Int = 100): Unit =
    val (hus, event) =
      HandlingUnitStock.create(containerId, slotCode, stockPositionId, true, onHand, at)
    esTestKit.runCommand[StatusReply[Done]](
      HandlingUnitStockActor.Create(hus, event, _)
    )

  describe("HandlingUnitStockActor"):

    describe("Create"):

      it("accepts Create command and transitions to ActiveState"):
        val (hus, event) =
          HandlingUnitStock.create(containerId, slotCode, stockPositionId, true, 100, at)
        val result = esTestKit.runCommand[StatusReply[Done]](
          HandlingUnitStockActor.Create(hus, event, _)
        )
        assert(result.reply.isSuccess)
        val state = result.stateOfType[HandlingUnitStockActor.ActiveState]
        assert(state.handlingUnitStock.onHandQuantity == 100)
        assert(state.handlingUnitStock.availableQuantity == 100)

      it("rejects Create when already active"):
        createHandlingUnitStock()
        val (hus, event) =
          HandlingUnitStock.create(containerId, slotCode, stockPositionId, true, 50, at)
        val result = esTestKit.runCommand[StatusReply[Done]](
          HandlingUnitStockActor.Create(hus, event, _)
        )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Allocate"):

      it("allocates and returns updated handling unit stock"):
        createHandlingUnitStock(onHand = 100)
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.Allocate(30, at, _)
          )
        assert(result.reply.isSuccess)
        val hus = result.reply.getValue.handlingUnitStock
        assert(hus.availableQuantity == 70)
        assert(hus.allocatedQuantity == 30)

      it("rejects Allocate on empty state"):
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.Allocate(10, at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Deallocate"):

      it("deallocates and returns updated handling unit stock"):
        createHandlingUnitStock(onHand = 100)
        esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
          HandlingUnitStockActor.Allocate(40, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.Deallocate(15, at, _)
          )
        assert(result.reply.isSuccess)
        val hus = result.reply.getValue.handlingUnitStock
        assert(hus.availableQuantity == 75)
        assert(hus.allocatedQuantity == 25)

    describe("AddQuantity"):

      it("adds quantity and returns updated handling unit stock"):
        createHandlingUnitStock(onHand = 50)
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.AddQuantity(30, at, _)
          )
        assert(result.reply.isSuccess)
        val hus = result.reply.getValue.handlingUnitStock
        assert(hus.onHandQuantity == 80)
        assert(hus.availableQuantity == 80)

    describe("ConsumeAllocated"):

      it("consumes allocated and returns updated handling unit stock"):
        createHandlingUnitStock(onHand = 100)
        esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
          HandlingUnitStockActor.Allocate(40, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.ConsumeAllocated(25, at, _)
          )
        assert(result.reply.isSuccess)
        val hus = result.reply.getValue.handlingUnitStock
        assert(hus.onHandQuantity == 75)
        assert(hus.allocatedQuantity == 15)
        assert(hus.availableQuantity == 60)

    describe("Reserve"):

      it("reserves and returns updated handling unit stock with lock type"):
        createHandlingUnitStock(onHand = 100)
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.Reserve(20, StockLockType.Count, at, _)
          )
        assert(result.reply.isSuccess)
        val hus = result.reply.getValue.handlingUnitStock
        assert(hus.availableQuantity == 80)
        assert(hus.reservedQuantity == 20)

    describe("ReleaseReservation"):

      it("releases reservation and returns updated handling unit stock"):
        createHandlingUnitStock(onHand = 100)
        esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
          HandlingUnitStockActor.Reserve(30, StockLockType.Count, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.ReleaseReservation(10, StockLockType.Count, at, _)
          )
        assert(result.reply.isSuccess)
        val hus = result.reply.getValue.handlingUnitStock
        assert(hus.reservedQuantity == 20)
        assert(hus.availableQuantity == 80)

    describe("Block and Unblock"):

      it("blocks and unblocks quantity"):
        createHandlingUnitStock(onHand = 100)
        esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
          HandlingUnitStockActor.Block(25, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.Unblock(10, at, _)
          )
        assert(result.reply.isSuccess)
        val hus = result.reply.getValue.handlingUnitStock
        assert(hus.blockedQuantity == 15)
        assert(hus.availableQuantity == 85)

    describe("Adjust"):

      it("adjusts quantity with reason code"):
        createHandlingUnitStock(onHand = 100)
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.Adjust(5, AdjustmentReasonCode.Found, at, _)
          )
        assert(result.reply.isSuccess)
        val hus = result.reply.getValue.handlingUnitStock
        assert(hus.onHandQuantity == 105)
        assert(hus.availableQuantity == 105)

    describe("ChangeStatus"):

      it("changes status and returns updated handling unit stock"):
        createHandlingUnitStock(onHand = 100)
        val result =
          esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
            HandlingUnitStockActor.ChangeStatus(InventoryStatus.QualityHold, at, _)
          )
        assert(result.reply.isSuccess)
        assert(result.reply.getValue.handlingUnitStock.status == InventoryStatus.QualityHold)

    describe("GetState"):

      it("returns None on empty state"):
        val result = esTestKit.runCommand[Option[HandlingUnitStock]](
          HandlingUnitStockActor.GetState(_)
        )
        assert(result.reply.isEmpty)

      it("returns handling unit stock on active state"):
        createHandlingUnitStock(onHand = 75)
        val result = esTestKit.runCommand[Option[HandlingUnitStock]](
          HandlingUnitStockActor.GetState(_)
        )
        assert(result.reply.isDefined)
        assert(result.reply.get.onHandQuantity == 75)

    describe("event replay"):

      it("recovers handling unit stock with correct quantities after restart"):
        createHandlingUnitStock(onHand = 100)
        esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
          HandlingUnitStockActor.Allocate(30, at, _)
        )
        esTestKit.runCommand[StatusReply[HandlingUnitStockActor.MutationResponse]](
          HandlingUnitStockActor.Reserve(10, StockLockType.Count, at, _)
        )
        esTestKit.restart()

        val result = esTestKit.runCommand[Option[HandlingUnitStock]](
          HandlingUnitStockActor.GetState(_)
        )
        assert(result.reply.isDefined)
        val hus = result.reply.get
        assert(hus.onHandQuantity == 100)
        assert(hus.allocatedQuantity == 30)
        assert(hus.reservedQuantity == 10)
        assert(hus.availableQuantity == 60)
