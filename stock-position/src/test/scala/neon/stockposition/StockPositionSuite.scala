package neon.stockposition

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
import org.scalatest.funspec.AnyFunSpec

import java.time.{Instant, LocalDate}

class StockPositionSuite extends AnyFunSpec:

  val skuId = SkuId()
  val warehouseAreaId = WarehouseAreaId()
  val lotAttributes = LotAttributes(
    lot = Some(Lot("LOT-001")),
    expirationDate = Some(LocalDate.of(2027, 6, 30))
  )
  val at = Instant.now()

  def aStockPosition(
      onHand: Int = 100,
      allocated: Int = 0,
      reserved: Int = 0,
      blocked: Int = 0
  ): StockPosition =
    val (sp, _) = StockPosition.create(skuId, warehouseAreaId, lotAttributes, onHand, at)
    val withAllocated =
      if allocated > 0 then sp.allocate(allocated, at)._1 else sp
    val withReserved =
      if reserved > 0 then withAllocated.reserve(reserved, StockLockType.Count, at)._1
      else withAllocated
    if blocked > 0 then withReserved.block(blocked, at)._1
    else withReserved

  describe("StockPosition"):

    describe("creating"):

      it("produces a StockPosition with available equal to onHand and a Created event"):
        val (sp, event) = StockPosition.create(skuId, warehouseAreaId, lotAttributes, 50, at)
        assert(sp.id == event.stockPositionId)
        assert(sp.skuId == skuId)
        assert(sp.warehouseAreaId == warehouseAreaId)
        assert(sp.lotAttributes == lotAttributes)
        assert(sp.status == InventoryStatus.Available)
        assert(sp.onHandQuantity == 50)
        assert(sp.availableQuantity == 50)
        assert(sp.allocatedQuantity == 0)
        assert(sp.reservedQuantity == 0)
        assert(sp.blockedQuantity == 0)

      it("sets available equal to onHand with all other buckets at zero"):
        val (sp, _) = StockPosition.create(skuId, warehouseAreaId, lotAttributes, 200, at)
        assert(sp.availableQuantity == sp.onHandQuantity)
        assert(sp.allocatedQuantity == 0)
        assert(sp.reservedQuantity == 0)
        assert(sp.blockedQuantity == 0)

      it("rejects negative initial quantity"):
        assertThrows[IllegalArgumentException]:
          StockPosition.create(skuId, warehouseAreaId, lotAttributes, -1, at)

      it("accepts zero initial quantity"):
        val (sp, _) = StockPosition.create(skuId, warehouseAreaId, lotAttributes, 0, at)
        assert(sp.onHandQuantity == 0)
        assert(sp.availableQuantity == 0)

      it("Created event carries all identity fields and quantity"):
        val (sp, event) = StockPosition.create(skuId, warehouseAreaId, lotAttributes, 75, at)
        assert(event.stockPositionId == sp.id)
        assert(event.skuId == skuId)
        assert(event.warehouseAreaId == warehouseAreaId)
        assert(event.onHandQuantity == 75)
        assert(event.occurredAt == at)

    describe("allocating"):

      it("moves quantity from available to allocated"):
        val sp = aStockPosition(onHand = 100)
        val (updated, _) = sp.allocate(30, at)
        assert(updated.availableQuantity == 70)
        assert(updated.allocatedQuantity == 30)
        assert(updated.onHandQuantity == 100)

      it("maintains the onHand invariant after allocation"):
        val sp = aStockPosition(onHand = 100)
        val (updated, _) = sp.allocate(40, at)
        assert(
          updated.onHandQuantity ==
            updated.availableQuantity + updated.allocatedQuantity +
            updated.reservedQuantity + updated.blockedQuantity
        )

      it("returns Allocated event with quantity and timestamp"):
        val sp = aStockPosition(onHand = 100)
        val (_, event) = sp.allocate(25, at)
        assert(event.stockPositionId == sp.id)
        assert(event.quantity == 25)
        assert(event.occurredAt == at)

      it("supports multiple sequential allocations"):
        val sp = aStockPosition(onHand = 100)
        val (sp1, _) = sp.allocate(20, at)
        val (sp2, _) = sp1.allocate(30, at)
        assert(sp2.availableQuantity == 50)
        assert(sp2.allocatedQuantity == 50)

      it("rejects allocation exceeding available quantity"):
        val sp = aStockPosition(onHand = 100)
        assertThrows[IllegalArgumentException]:
          sp.allocate(101, at)

      it("rejects zero allocation quantity"):
        val sp = aStockPosition(onHand = 100)
        assertThrows[IllegalArgumentException]:
          sp.allocate(0, at)

      it("rejects negative allocation quantity"):
        val sp = aStockPosition(onHand = 100)
        assertThrows[IllegalArgumentException]:
          sp.allocate(-5, at)

    describe("deallocating"):

      it("moves quantity from allocated back to available"):
        val sp = aStockPosition(onHand = 100, allocated = 40)
        val (updated, _) = sp.deallocate(15, at)
        assert(updated.availableQuantity == 75)
        assert(updated.allocatedQuantity == 25)
        assert(updated.onHandQuantity == 100)

      it("returns Deallocated event"):
        val sp = aStockPosition(onHand = 100, allocated = 40)
        val (_, event) = sp.deallocate(10, at)
        assert(event.stockPositionId == sp.id)
        assert(event.quantity == 10)
        assert(event.occurredAt == at)

      it("rejects deallocation exceeding allocated quantity"):
        val sp = aStockPosition(onHand = 100, allocated = 20)
        assertThrows[IllegalArgumentException]:
          sp.deallocate(21, at)

    describe("adding quantity"):

      it("increases both onHand and available by the added amount"):
        val sp = aStockPosition(onHand = 50)
        val (updated, _) = sp.addQuantity(30, at)
        assert(updated.onHandQuantity == 80)
        assert(updated.availableQuantity == 80)

      it("preserves existing allocations when adding"):
        val sp = aStockPosition(onHand = 100, allocated = 20)
        val (updated, _) = sp.addQuantity(50, at)
        assert(updated.onHandQuantity == 150)
        assert(updated.availableQuantity == 130)
        assert(updated.allocatedQuantity == 20)

      it("returns QuantityAdded event"):
        val sp = aStockPosition(onHand = 50)
        val (_, event) = sp.addQuantity(25, at)
        assert(event.stockPositionId == sp.id)
        assert(event.quantity == 25)
        assert(event.occurredAt == at)

      it("rejects zero quantity"):
        val sp = aStockPosition(onHand = 50)
        assertThrows[IllegalArgumentException]:
          sp.addQuantity(0, at)

      it("rejects negative quantity"):
        val sp = aStockPosition(onHand = 50)
        assertThrows[IllegalArgumentException]:
          sp.addQuantity(-10, at)

    describe("consuming allocated"):

      it("decreases both onHand and allocated by consumed amount"):
        val sp = aStockPosition(onHand = 100, allocated = 40)
        val (updated, _) = sp.consumeAllocated(25, at)
        assert(updated.onHandQuantity == 75)
        assert(updated.allocatedQuantity == 15)

      it("leaves available unchanged when consuming allocated"):
        val sp = aStockPosition(onHand = 100, allocated = 40)
        val availableBefore = sp.availableQuantity
        val (updated, _) = sp.consumeAllocated(25, at)
        assert(updated.availableQuantity == availableBefore)

      it("returns AllocatedConsumed event"):
        val sp = aStockPosition(onHand = 100, allocated = 30)
        val (_, event) = sp.consumeAllocated(10, at)
        assert(event.stockPositionId == sp.id)
        assert(event.quantity == 10)
        assert(event.occurredAt == at)

      it("rejects consumption exceeding allocated quantity"):
        val sp = aStockPosition(onHand = 100, allocated = 20)
        assertThrows[IllegalArgumentException]:
          sp.consumeAllocated(21, at)

    describe("reserving"):

      it("moves quantity from available to reserved"):
        val sp = aStockPosition(onHand = 100)
        val (updated, _) = sp.reserve(30, StockLockType.Count, at)
        assert(updated.availableQuantity == 70)
        assert(updated.reservedQuantity == 30)
        assert(updated.onHandQuantity == 100)

      it("carries lock type on the Reserved event"):
        val sp = aStockPosition(onHand = 100)
        val (_, event) = sp.reserve(20, StockLockType.InternalMove, at)
        assert(event.lockType == StockLockType.InternalMove)
        assert(event.quantity == 20)

      it("rejects reservation exceeding available quantity"):
        val sp = aStockPosition(onHand = 100, allocated = 80)
        assertThrows[IllegalArgumentException]:
          sp.reserve(21, StockLockType.Count, at)

    describe("releasing reservation"):

      it("moves quantity from reserved back to available"):
        val sp = aStockPosition(onHand = 100, reserved = 30)
        val (updated, _) = sp.releaseReservation(20, StockLockType.Count, at)
        assert(updated.reservedQuantity == 10)
        assert(updated.availableQuantity == 90)

      it("carries lock type on the ReservationReleased event"):
        val sp = aStockPosition(onHand = 100, reserved = 30)
        val (_, event) = sp.releaseReservation(10, StockLockType.Count, at)
        assert(event.lockType == StockLockType.Count)
        assert(event.quantity == 10)

      it("rejects release exceeding reserved quantity"):
        val sp = aStockPosition(onHand = 100, reserved = 15)
        assertThrows[IllegalArgumentException]:
          sp.releaseReservation(16, StockLockType.Count, at)

    describe("blocking"):

      it("moves quantity from available to blocked"):
        val sp = aStockPosition(onHand = 100)
        val (updated, _) = sp.block(25, at)
        assert(updated.availableQuantity == 75)
        assert(updated.blockedQuantity == 25)
        assert(updated.onHandQuantity == 100)

      it("returns Blocked event"):
        val sp = aStockPosition(onHand = 100)
        val (_, event) = sp.block(25, at)
        assert(event.stockPositionId == sp.id)
        assert(event.quantity == 25)

      it("rejects blocking more than available"):
        val sp = aStockPosition(onHand = 100, allocated = 80)
        assertThrows[IllegalArgumentException]:
          sp.block(21, at)

    describe("unblocking"):

      it("moves quantity from blocked back to available"):
        val sp = aStockPosition(onHand = 100, blocked = 30)
        val (updated, _) = sp.unblock(20, at)
        assert(updated.blockedQuantity == 10)
        assert(updated.availableQuantity == 90)

      it("rejects unblocking more than blocked"):
        val sp = aStockPosition(onHand = 100, blocked = 10)
        assertThrows[IllegalArgumentException]:
          sp.unblock(11, at)

    describe("adjusting"):

      it("increases onHand and available for positive delta"):
        val sp = aStockPosition(onHand = 100)
        val (updated, _) = sp.adjust(15, AdjustmentReasonCode.Found, at)
        assert(updated.onHandQuantity == 115)
        assert(updated.availableQuantity == 115)

      it("decreases onHand and available for negative delta"):
        val sp = aStockPosition(onHand = 100)
        val (updated, _) = sp.adjust(-10, AdjustmentReasonCode.Shrinkage, at)
        assert(updated.onHandQuantity == 90)
        assert(updated.availableQuantity == 90)

      it("carries reason code on Adjusted event"):
        val sp = aStockPosition(onHand = 100)
        val (_, event) = sp.adjust(5, AdjustmentReasonCode.CycleCountAdjustment, at)
        assert(event.reasonCode == AdjustmentReasonCode.CycleCountAdjustment)
        assert(event.delta == 5)

      it("rejects adjustment that would make available negative"):
        val sp = aStockPosition(onHand = 100, allocated = 80)
        assertThrows[IllegalArgumentException]:
          sp.adjust(-21, AdjustmentReasonCode.Shrinkage, at)

    describe("changing status"):

      it("transitions to new status and returns StatusChanged event"):
        val sp = aStockPosition(onHand = 100)
        val (updated, event) = sp.changeStatus(InventoryStatus.QualityHold, at)
        assert(updated.status == InventoryStatus.QualityHold)
        assert(event.previousStatus == InventoryStatus.Available)
        assert(event.newStatus == InventoryStatus.QualityHold)

      it("carries stock position id and timestamp on event"):
        val sp = aStockPosition(onHand = 100)
        val (_, event) = sp.changeStatus(InventoryStatus.Damaged, at)
        assert(event.stockPositionId == sp.id)
        assert(event.occurredAt == at)
