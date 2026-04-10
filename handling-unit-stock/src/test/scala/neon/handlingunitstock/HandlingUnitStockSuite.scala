package neon.handlingunitstock

import neon.common.{
  AdjustmentReasonCode,
  ContainerId,
  HandlingUnitStockId,
  InventoryStatus,
  SkuId,
  SlotCode,
  StockLockType,
  StockPositionId
}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class HandlingUnitStockSuite extends AnyFunSpec:

  val skuId = SkuId()
  val containerId = ContainerId()
  val slotCode = SlotCode("A-01")
  val stockPositionId = StockPositionId()
  val at = Instant.now()

  def aHandlingUnitStock(
      onHand: Int = 100,
      allocated: Int = 0,
      reserved: Int = 0,
      blocked: Int = 0,
      physicalContainer: Boolean = true
  ): HandlingUnitStock =
    val (hus, _) =
      HandlingUnitStock.create(
        skuId,
        containerId,
        slotCode,
        stockPositionId,
        physicalContainer,
        onHand,
        at
      )
    val withAllocated =
      if allocated > 0 then hus.allocate(allocated, at)._1 else hus
    val withReserved =
      if reserved > 0 then withAllocated.reserve(reserved, StockLockType.Count, at)._1
      else withAllocated
    if blocked > 0 then withReserved.block(blocked, at)._1
    else withReserved

  describe("HandlingUnitStock"):

    describe("creating"):

      it("produces a HandlingUnitStock with available equal to onHand and a Created event"):
        val (hus, event) =
          HandlingUnitStock.create(skuId, containerId, slotCode, stockPositionId, true, 50, at)
        assert(hus.id == event.handlingUnitStockId)
        assert(hus.containerId == containerId)
        assert(hus.slotCode == slotCode)
        assert(hus.stockPositionId == stockPositionId)
        assert(hus.physicalContainer == true)
        assert(hus.status == InventoryStatus.Available)
        assert(hus.onHandQuantity == 50)
        assert(hus.availableQuantity == 50)
        assert(hus.allocatedQuantity == 0)
        assert(hus.reservedQuantity == 0)
        assert(hus.blockedQuantity == 0)

      it("sets available equal to onHand with all other buckets at zero"):
        val (hus, _) =
          HandlingUnitStock.create(skuId, containerId, slotCode, stockPositionId, true, 200, at)
        assert(hus.availableQuantity == hus.onHandQuantity)
        assert(hus.allocatedQuantity == 0)
        assert(hus.reservedQuantity == 0)
        assert(hus.blockedQuantity == 0)

      it("rejects negative initial quantity"):
        assertThrows[IllegalArgumentException]:
          HandlingUnitStock.create(skuId, containerId, slotCode, stockPositionId, true, -1, at)

      it("accepts zero initial quantity"):
        val (hus, _) =
          HandlingUnitStock.create(skuId, containerId, slotCode, stockPositionId, true, 0, at)
        assert(hus.onHandQuantity == 0)
        assert(hus.availableQuantity == 0)

      it("supports logical containers with physicalContainer set to false"):
        val (hus, event) =
          HandlingUnitStock.create(skuId, containerId, slotCode, stockPositionId, false, 25, at)
        assert(hus.physicalContainer == false)
        assert(event.physicalContainer == false)

      it("Created event carries all identity fields and quantity"):
        val (hus, event) =
          HandlingUnitStock.create(skuId, containerId, slotCode, stockPositionId, true, 75, at)
        assert(event.handlingUnitStockId == hus.id)
        assert(event.containerId == containerId)
        assert(event.slotCode == slotCode)
        assert(event.stockPositionId == stockPositionId)
        assert(event.physicalContainer == true)
        assert(event.onHandQuantity == 75)
        assert(event.occurredAt == at)

    describe("allocating"):

      it("moves quantity from available to allocated"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (updated, _) = hus.allocate(30, at)
        assert(updated.availableQuantity == 70)
        assert(updated.allocatedQuantity == 30)
        assert(updated.onHandQuantity == 100)

      it("maintains the onHand invariant after allocation"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (updated, _) = hus.allocate(40, at)
        assert(
          updated.onHandQuantity ==
            updated.availableQuantity + updated.allocatedQuantity +
            updated.reservedQuantity + updated.blockedQuantity
        )

      it("returns Allocated event with quantity and timestamp"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (_, event) = hus.allocate(25, at)
        assert(event.handlingUnitStockId == hus.id)
        assert(event.quantity == 25)
        assert(event.occurredAt == at)

      it("supports multiple sequential allocations"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (hus1, _) = hus.allocate(20, at)
        val (hus2, _) = hus1.allocate(30, at)
        assert(hus2.availableQuantity == 50)
        assert(hus2.allocatedQuantity == 50)

      it("rejects allocation exceeding available quantity"):
        val hus = aHandlingUnitStock(onHand = 100)
        assertThrows[IllegalArgumentException]:
          hus.allocate(101, at)

      it("rejects zero allocation quantity"):
        val hus = aHandlingUnitStock(onHand = 100)
        assertThrows[IllegalArgumentException]:
          hus.allocate(0, at)

      it("rejects negative allocation quantity"):
        val hus = aHandlingUnitStock(onHand = 100)
        assertThrows[IllegalArgumentException]:
          hus.allocate(-5, at)

    describe("deallocating"):

      it("moves quantity from allocated back to available"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 40)
        val (updated, _) = hus.deallocate(15, at)
        assert(updated.availableQuantity == 75)
        assert(updated.allocatedQuantity == 25)
        assert(updated.onHandQuantity == 100)

      it("returns Deallocated event"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 40)
        val (_, event) = hus.deallocate(10, at)
        assert(event.handlingUnitStockId == hus.id)
        assert(event.quantity == 10)
        assert(event.occurredAt == at)

      it("rejects deallocation exceeding allocated quantity"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 20)
        assertThrows[IllegalArgumentException]:
          hus.deallocate(21, at)

    describe("adding quantity"):

      it("increases both onHand and available by the added amount"):
        val hus = aHandlingUnitStock(onHand = 50)
        val (updated, _) = hus.addQuantity(30, at)
        assert(updated.onHandQuantity == 80)
        assert(updated.availableQuantity == 80)

      it("preserves existing allocations when adding"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 20)
        val (updated, _) = hus.addQuantity(50, at)
        assert(updated.onHandQuantity == 150)
        assert(updated.availableQuantity == 130)
        assert(updated.allocatedQuantity == 20)

      it("returns QuantityAdded event"):
        val hus = aHandlingUnitStock(onHand = 50)
        val (_, event) = hus.addQuantity(25, at)
        assert(event.handlingUnitStockId == hus.id)
        assert(event.quantity == 25)
        assert(event.occurredAt == at)

      it("rejects zero quantity"):
        val hus = aHandlingUnitStock(onHand = 50)
        assertThrows[IllegalArgumentException]:
          hus.addQuantity(0, at)

      it("rejects negative quantity"):
        val hus = aHandlingUnitStock(onHand = 50)
        assertThrows[IllegalArgumentException]:
          hus.addQuantity(-10, at)

    describe("consuming allocated"):

      it("decreases both onHand and allocated by consumed amount"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 40)
        val (updated, _) = hus.consumeAllocated(25, at)
        assert(updated.onHandQuantity == 75)
        assert(updated.allocatedQuantity == 15)

      it("leaves available unchanged when consuming allocated"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 40)
        val availableBefore = hus.availableQuantity
        val (updated, _) = hus.consumeAllocated(25, at)
        assert(updated.availableQuantity == availableBefore)

      it("returns AllocatedConsumed event"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 30)
        val (_, event) = hus.consumeAllocated(10, at)
        assert(event.handlingUnitStockId == hus.id)
        assert(event.quantity == 10)
        assert(event.occurredAt == at)

      it("rejects consumption exceeding allocated quantity"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 20)
        assertThrows[IllegalArgumentException]:
          hus.consumeAllocated(21, at)

    describe("reserving"):

      it("moves quantity from available to reserved"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (updated, _) = hus.reserve(30, StockLockType.Count, at)
        assert(updated.availableQuantity == 70)
        assert(updated.reservedQuantity == 30)
        assert(updated.onHandQuantity == 100)

      it("carries lock type on the Reserved event"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (_, event) = hus.reserve(20, StockLockType.InternalMove, at)
        assert(event.lockType == StockLockType.InternalMove)
        assert(event.quantity == 20)

      it("rejects reservation exceeding available quantity"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 80)
        assertThrows[IllegalArgumentException]:
          hus.reserve(21, StockLockType.Count, at)

    describe("releasing reservation"):

      it("moves quantity from reserved back to available"):
        val hus = aHandlingUnitStock(onHand = 100, reserved = 30)
        val (updated, _) = hus.releaseReservation(20, StockLockType.Count, at)
        assert(updated.reservedQuantity == 10)
        assert(updated.availableQuantity == 90)

      it("carries lock type on the ReservationReleased event"):
        val hus = aHandlingUnitStock(onHand = 100, reserved = 30)
        val (_, event) = hus.releaseReservation(10, StockLockType.Count, at)
        assert(event.lockType == StockLockType.Count)
        assert(event.quantity == 10)

      it("rejects release exceeding reserved quantity"):
        val hus = aHandlingUnitStock(onHand = 100, reserved = 15)
        assertThrows[IllegalArgumentException]:
          hus.releaseReservation(16, StockLockType.Count, at)

    describe("blocking"):

      it("moves quantity from available to blocked"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (updated, _) = hus.block(25, at)
        assert(updated.availableQuantity == 75)
        assert(updated.blockedQuantity == 25)
        assert(updated.onHandQuantity == 100)

      it("returns Blocked event"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (_, event) = hus.block(25, at)
        assert(event.handlingUnitStockId == hus.id)
        assert(event.quantity == 25)

      it("rejects blocking more than available"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 80)
        assertThrows[IllegalArgumentException]:
          hus.block(21, at)

    describe("unblocking"):

      it("moves quantity from blocked back to available"):
        val hus = aHandlingUnitStock(onHand = 100, blocked = 30)
        val (updated, _) = hus.unblock(20, at)
        assert(updated.blockedQuantity == 10)
        assert(updated.availableQuantity == 90)

      it("rejects unblocking more than blocked"):
        val hus = aHandlingUnitStock(onHand = 100, blocked = 10)
        assertThrows[IllegalArgumentException]:
          hus.unblock(11, at)

    describe("adjusting"):

      it("increases onHand and available for positive delta"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (updated, _) = hus.adjust(15, AdjustmentReasonCode.Found, at)
        assert(updated.onHandQuantity == 115)
        assert(updated.availableQuantity == 115)

      it("decreases onHand and available for negative delta"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (updated, _) = hus.adjust(-10, AdjustmentReasonCode.Shrinkage, at)
        assert(updated.onHandQuantity == 90)
        assert(updated.availableQuantity == 90)

      it("carries reason code on Adjusted event"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (_, event) = hus.adjust(5, AdjustmentReasonCode.CycleCountAdjustment, at)
        assert(event.reasonCode == AdjustmentReasonCode.CycleCountAdjustment)
        assert(event.delta == 5)

      it("rejects adjustment that would make available negative"):
        val hus = aHandlingUnitStock(onHand = 100, allocated = 80)
        assertThrows[IllegalArgumentException]:
          hus.adjust(-21, AdjustmentReasonCode.Shrinkage, at)

    describe("changing status"):

      it("transitions to new status and returns StatusChanged event"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (updated, event) = hus.changeStatus(InventoryStatus.QualityHold, at)
        assert(updated.status == InventoryStatus.QualityHold)
        assert(event.previousStatus == InventoryStatus.Available)
        assert(event.newStatus == InventoryStatus.QualityHold)

      it("carries handling unit stock id and timestamp on event"):
        val hus = aHandlingUnitStock(onHand = 100)
        val (_, event) = hus.changeStatus(InventoryStatus.Damaged, at)
        assert(event.handlingUnitStockId == hus.id)
        assert(event.occurredAt == at)
