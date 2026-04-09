package neon.stockposition

import com.typesafe.config.ConfigFactory
import neon.common.{
  AdjustmentReasonCode,
  InventoryStatus,
  Lot,
  LotAttributes,
  SkuId,
  StockLockType,
  StockPositionId,
  WarehouseAreaId
}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.{Instant, LocalDate}

class StockPositionActorSuite
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

  private val stockPositionId = StockPositionId()
  private val skuId = SkuId()
  private val warehouseAreaId = WarehouseAreaId()
  private val lotAttributes = LotAttributes(
    lot = Some(Lot("LOT-001")),
    expirationDate = Some(LocalDate.of(2027, 6, 30))
  )
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    StockPositionActor.Command,
    StockPositionEvent,
    StockPositionActor.State
  ](
    system,
    StockPositionActor(stockPositionId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createStockPosition(onHand: Int = 100): Unit =
    val (sp, event) = StockPosition.create(skuId, warehouseAreaId, lotAttributes, onHand, at)
    esTestKit.runCommand[StatusReply[Done]](
      StockPositionActor.Create(sp, event, _)
    )

  describe("StockPositionActor"):

    describe("Create"):

      it("accepts Create command and transitions to ActiveState"):
        val (sp, event) = StockPosition.create(skuId, warehouseAreaId, lotAttributes, 100, at)
        val result = esTestKit.runCommand[StatusReply[Done]](
          StockPositionActor.Create(sp, event, _)
        )
        assert(result.reply.isSuccess)
        val state = result.stateOfType[StockPositionActor.ActiveState]
        assert(state.stockPosition.onHandQuantity == 100)
        assert(state.stockPosition.availableQuantity == 100)

      it("rejects Create when already active"):
        createStockPosition()
        val (sp, event) = StockPosition.create(skuId, warehouseAreaId, lotAttributes, 50, at)
        val result = esTestKit.runCommand[StatusReply[Done]](
          StockPositionActor.Create(sp, event, _)
        )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Allocate"):

      it("allocates and returns updated stock position"):
        createStockPosition(onHand = 100)
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Allocate(30, at, _)
        )
        assert(result.reply.isSuccess)
        val sp = result.reply.getValue.stockPosition
        assert(sp.availableQuantity == 70)
        assert(sp.allocatedQuantity == 30)

      it("rejects Allocate on empty state"):
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Allocate(10, at, _)
        )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Deallocate"):

      it("deallocates and returns updated stock position"):
        createStockPosition(onHand = 100)
        esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Allocate(40, at, _)
        )
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Deallocate(15, at, _)
        )
        assert(result.reply.isSuccess)
        val sp = result.reply.getValue.stockPosition
        assert(sp.availableQuantity == 75)
        assert(sp.allocatedQuantity == 25)

    describe("AddQuantity"):

      it("adds quantity and returns updated stock position"):
        createStockPosition(onHand = 50)
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.AddQuantity(30, at, _)
        )
        assert(result.reply.isSuccess)
        val sp = result.reply.getValue.stockPosition
        assert(sp.onHandQuantity == 80)
        assert(sp.availableQuantity == 80)

    describe("ConsumeAllocated"):

      it("consumes allocated and returns updated stock position"):
        createStockPosition(onHand = 100)
        esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Allocate(40, at, _)
        )
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.ConsumeAllocated(25, at, _)
        )
        assert(result.reply.isSuccess)
        val sp = result.reply.getValue.stockPosition
        assert(sp.onHandQuantity == 75)
        assert(sp.allocatedQuantity == 15)
        assert(sp.availableQuantity == 60)

    describe("Reserve"):

      it("reserves and returns updated stock position with lock type"):
        createStockPosition(onHand = 100)
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Reserve(20, StockLockType.Count, at, _)
        )
        assert(result.reply.isSuccess)
        val sp = result.reply.getValue.stockPosition
        assert(sp.availableQuantity == 80)
        assert(sp.reservedQuantity == 20)

    describe("ReleaseReservation"):

      it("releases reservation and returns updated stock position"):
        createStockPosition(onHand = 100)
        esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Reserve(30, StockLockType.Count, at, _)
        )
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.ReleaseReservation(10, StockLockType.Count, at, _)
        )
        assert(result.reply.isSuccess)
        val sp = result.reply.getValue.stockPosition
        assert(sp.reservedQuantity == 20)
        assert(sp.availableQuantity == 80)

    describe("Block and Unblock"):

      it("blocks and unblocks quantity"):
        createStockPosition(onHand = 100)
        esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Block(25, at, _)
        )
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Unblock(10, at, _)
        )
        assert(result.reply.isSuccess)
        val sp = result.reply.getValue.stockPosition
        assert(sp.blockedQuantity == 15)
        assert(sp.availableQuantity == 85)

    describe("Adjust"):

      it("adjusts quantity with reason code"):
        createStockPosition(onHand = 100)
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Adjust(5, AdjustmentReasonCode.Found, at, _)
        )
        assert(result.reply.isSuccess)
        val sp = result.reply.getValue.stockPosition
        assert(sp.onHandQuantity == 105)
        assert(sp.availableQuantity == 105)

    describe("ChangeStatus"):

      it("changes status and returns updated stock position"):
        createStockPosition(onHand = 100)
        val result = esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.ChangeStatus(InventoryStatus.QualityHold, at, _)
        )
        assert(result.reply.isSuccess)
        assert(result.reply.getValue.stockPosition.status == InventoryStatus.QualityHold)

    describe("GetState"):

      it("returns None on empty state"):
        val result = esTestKit.runCommand[Option[StockPosition]](
          StockPositionActor.GetState(_)
        )
        assert(result.reply.isEmpty)

      it("returns stock position on active state"):
        createStockPosition(onHand = 75)
        val result = esTestKit.runCommand[Option[StockPosition]](
          StockPositionActor.GetState(_)
        )
        assert(result.reply.isDefined)
        assert(result.reply.get.onHandQuantity == 75)

    describe("event replay"):

      it("recovers stock position with correct quantities after restart"):
        createStockPosition(onHand = 100)
        esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Allocate(30, at, _)
        )
        esTestKit.runCommand[StatusReply[StockPositionActor.MutationResponse]](
          StockPositionActor.Reserve(10, StockLockType.Count, at, _)
        )
        esTestKit.restart()

        val result = esTestKit.runCommand[Option[StockPosition]](
          StockPositionActor.GetState(_)
        )
        assert(result.reply.isDefined)
        val sp = result.reply.get
        assert(sp.onHandQuantity == 100)
        assert(sp.allocatedQuantity == 30)
        assert(sp.reservedQuantity == 10)
        assert(sp.availableQuantity == 60)
