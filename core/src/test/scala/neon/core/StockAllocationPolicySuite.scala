package neon.core

import neon.common.{
  AllocationStrategy,
  InventoryStatus,
  Lot,
  LotAttributes,
  SkuId,
  StockPositionId,
  WarehouseAreaId
}
import neon.stockposition.StockPosition
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec

import java.time.{Instant, LocalDate}

class StockAllocationPolicySuite extends AnyFunSpec with EitherValues:

  val skuId = SkuId()
  val skuId2 = SkuId()
  val warehouseAreaId = WarehouseAreaId()
  val at = Instant.now()
  val referenceDate = LocalDate.of(2027, 1, 1)

  def aStockPosition(
      skuId: SkuId = skuId,
      available: Int = 100,
      expirationDate: Option[LocalDate] = None,
      productionDate: Option[LocalDate] = None
  ): StockPosition =
    val lotAttrs = LotAttributes(
      lot = Some(Lot("LOT")),
      expirationDate = expirationDate,
      productionDate = productionDate
    )
    StockPosition.create(skuId, warehouseAreaId, lotAttrs, available, at)._1

  def request(skuId: SkuId = skuId, quantity: Int = 10): AllocationRequest =
    AllocationRequest(skuId, quantity)

  describe("StockAllocationPolicy"):

    describe("FEFO strategy"):

      it("allocates from the position with the earliest expiration date"):
        val earlyExpiry = aStockPosition(
          expirationDate = Some(LocalDate.of(2027, 3, 1))
        )
        val lateExpiry = aStockPosition(
          expirationDate = Some(LocalDate.of(2027, 9, 1))
        )
        val stock = Map(skuId -> List(lateExpiry, earlyExpiry))
        val result = StockAllocationPolicy(
          List(request(quantity = 10)),
          stock,
          AllocationStrategy.Fefo,
          referenceDate
        ).value
        assert(result.head.allocations.head.stockPositionId == earlyExpiry.id)

      it("uses production date as FIFO tiebreaker when expiration dates are equal"):
        val olderProduction = aStockPosition(
          expirationDate = Some(LocalDate.of(2027, 6, 1)),
          productionDate = Some(LocalDate.of(2026, 1, 1))
        )
        val newerProduction = aStockPosition(
          expirationDate = Some(LocalDate.of(2027, 6, 1)),
          productionDate = Some(LocalDate.of(2026, 6, 1))
        )
        val stock = Map(skuId -> List(newerProduction, olderProduction))
        val result = StockAllocationPolicy(
          List(request(quantity = 10)),
          stock,
          AllocationStrategy.Fefo,
          referenceDate
        ).value
        assert(result.head.allocations.head.stockPositionId == olderProduction.id)

      it("prefers smaller positions as tertiary sort to consolidate partial stock"):
        val small = aStockPosition(
          available = 15,
          expirationDate = Some(LocalDate.of(2027, 6, 1)),
          productionDate = Some(LocalDate.of(2026, 1, 1))
        )
        val large = aStockPosition(
          available = 200,
          expirationDate = Some(LocalDate.of(2027, 6, 1)),
          productionDate = Some(LocalDate.of(2026, 1, 1))
        )
        val stock = Map(skuId -> List(large, small))
        val result = StockAllocationPolicy(
          List(request(quantity = 10)),
          stock,
          AllocationStrategy.Fefo,
          referenceDate
        ).value
        assert(result.head.allocations.head.stockPositionId == small.id)

    describe("FIFO strategy"):

      it("allocates from the position with the earliest production date"):
        val olderProduction = aStockPosition(
          productionDate = Some(LocalDate.of(2026, 1, 1))
        )
        val newerProduction = aStockPosition(
          productionDate = Some(LocalDate.of(2026, 6, 1))
        )
        val stock = Map(skuId -> List(newerProduction, olderProduction))
        val result = StockAllocationPolicy(
          List(request(quantity = 10)),
          stock,
          AllocationStrategy.Fifo,
          referenceDate
        ).value
        assert(result.head.allocations.head.stockPositionId == olderProduction.id)

    describe("partial allocation"):

      it("splits across multiple positions when one is insufficient"):
        val small = aStockPosition(available = 7)
        val large = aStockPosition(available = 50)
        val stock = Map(skuId -> List(small, large))
        val result = StockAllocationPolicy(
          List(request(quantity = 20)),
          stock,
          AllocationStrategy.Fifo,
          referenceDate
        ).value
        val allocations = result.head.allocations
        assert(allocations.size == 2)
        assert(allocations.map(_.quantity).sum == 20)

      it("allocates what is available when total stock is insufficient"):
        val position = aStockPosition(available = 5)
        val stock = Map(skuId -> List(position))
        val result = StockAllocationPolicy(
          List(request(quantity = 20)),
          stock,
          AllocationStrategy.Fifo,
          referenceDate
        ).value
        assert(result.head.allocations.map(_.quantity).sum == 5)
        assert(result.head.shortQuantity == 15)

    describe("insufficient stock"):

      it("returns InsufficientStock when no stock exists for the SKU"):
        val stock = Map.empty[SkuId, List[StockPosition]]
        val result = StockAllocationPolicy(
          List(request(quantity = 10)),
          stock,
          AllocationStrategy.Fifo,
          referenceDate
        )
        assert(result.left.value.isInstanceOf[StockAllocationError.InsufficientStock])

    describe("shelf life validation"):

      it("skips positions with insufficient remaining shelf life"):
        val expiringSoon = aStockPosition(
          available = 100,
          expirationDate = Some(LocalDate.of(2027, 1, 15))
        )
        val expiringLater = aStockPosition(
          available = 100,
          expirationDate = Some(LocalDate.of(2027, 12, 1))
        )
        val stock = Map(skuId -> List(expiringSoon, expiringLater))
        val result = StockAllocationPolicy(
          List(request(quantity = 10)),
          stock,
          AllocationStrategy.Fefo,
          referenceDate,
          minimumShelfLifeDays = 30
        ).value
        assert(result.head.allocations.head.stockPositionId == expiringLater.id)

      it("returns InsufficientShelfLife when all positions have too little shelf life"):
        val expiringSoon = aStockPosition(
          available = 100,
          expirationDate = Some(LocalDate.of(2027, 1, 10))
        )
        val stock = Map(skuId -> List(expiringSoon))
        val result = StockAllocationPolicy(
          List(request(quantity = 10)),
          stock,
          AllocationStrategy.Fefo,
          referenceDate,
          minimumShelfLifeDays = 30
        )
        assert(result.left.value.isInstanceOf[StockAllocationError.InsufficientShelfLife])

    describe("multiple requests"):

      it("allocates independently for each request"):
        val position1 = aStockPosition(skuId = skuId, available = 50)
        val position2 = aStockPosition(skuId = skuId2, available = 30)
        val stock = Map(skuId -> List(position1), skuId2 -> List(position2))
        val result = StockAllocationPolicy(
          List(request(skuId = skuId, quantity = 10), request(skuId = skuId2, quantity = 5)),
          stock,
          AllocationStrategy.Fifo,
          referenceDate
        ).value
        assert(result.size == 2)
        assert(result(0).allocations.head.quantity == 10)
        assert(result(1).allocations.head.quantity == 5)

    describe("skips non-available positions"):

      it("ignores positions with non-Available status"):
        val (blockedPos, _) = aStockPosition(available = 100)
          .changeStatus(InventoryStatus.Blocked, at)
        val availablePos = aStockPosition(available = 50)
        val stock = Map(skuId -> List(blockedPos, availablePos))
        val result = StockAllocationPolicy(
          List(request(quantity = 10)),
          stock,
          AllocationStrategy.Fifo,
          referenceDate
        ).value
        assert(result.head.allocations.head.stockPositionId == availablePos.id)
