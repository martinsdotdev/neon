package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, UomHierarchy, WaveId}
import neon.order.OrderLine
import org.scalatest.funspec.AnyFunSpec

class UomExpansionSuite extends AnyFunSpec:
  val waveId = WaveId()
  val orderId = OrderId()
  val skuId = SkuId()

  describe("UomExpansion"):
    it("expands Each line into Pallet + Case + Each when SKU has full hierarchy"):
      val line = OrderLine(skuId, PackagingLevel.Each, 28)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result.length == 3)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Case && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

    it("creates a single Each task for empty hierarchy"):
      val line = OrderLine(skuId, PackagingLevel.Each, 28)
      val result = UomExpansion(waveId, orderId, line, UomHierarchy.empty)
      assert(result.length == 1)
      assert(result.head.packagingLevel == PackagingLevel.Each)
      assert(result.head.quantity == 28)

    it("expands with Case-only hierarchy into Case + Each"):
      val line = OrderLine(skuId, PackagingLevel.Each, 14)
      val hierarchy = UomHierarchy(PackagingLevel.Case -> 6)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result.length == 2)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Case && r.quantity == 2))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

    it("expands non-Each line but produces same level when no coarser tier fits"):
      val caseLine = OrderLine(skuId, PackagingLevel.Case, 3)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val caseResult = UomExpansion(waveId, orderId, caseLine, hierarchy)
      // 3 Cases = 18 eaches; Pallet=20 doesn't fit, so Case(3) remains
      assert(caseResult == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Case, 3)))

    it("expands Case line to Pallet when quantity is sufficient"):
      val line = OrderLine(skuId, PackagingLevel.Case, 10)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      // 10 Cases = 60 eaches → Pallet(3) = 60 eaches
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Pallet, 3)))

    it("expands Case line into Pallet + Case + Each"):
      val line = OrderLine(skuId, PackagingLevel.Case, 5)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      // 5 Cases = 30 eaches → Pallet(1)=20 + Case(1)=6 + Each(4)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result.length == 3)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Case && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 4))

    it("passes through when line level is not in hierarchy"):
      val line = OrderLine(skuId, PackagingLevel.InnerPack, 3)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      // InnerPack not in hierarchy → can't convert to eaches → pass-through
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.InnerPack, 3)))

    it("conserves total eaches when expanding non-Each line"):
      val line = OrderLine(skuId, PackagingLevel.Case, 10)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      val totalEaches = result
        .map: r =>
          if r.packagingLevel == PackagingLevel.Each then r.quantity
          else r.quantity * hierarchy(r.packagingLevel)
        .sum
      assert(totalEaches == 60)

    it("propagates wave ID and order ID in all expanded task requests"):
      val line = OrderLine(skuId, PackagingLevel.Each, 28)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result.forall(_.waveId == waveId))
      assert(result.forall(_.orderId == orderId))

    it("produces exactly one Pallet task when quantity is an exact multiple"):
      val line = OrderLine(skuId, PackagingLevel.Each, 20)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result.length == 1)
      assert(result.head.packagingLevel == PackagingLevel.Pallet)
      assert(result.head.quantity == 1)

    it("expands with a four-level hierarchy including InnerPack"):
      val line = OrderLine(skuId, PackagingLevel.Each, 30)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 24, PackagingLevel.InnerPack -> 4)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result.length == 3)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.InnerPack && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

    it("conserves total eaches across all expanded tiers"):
      val line = OrderLine(skuId, PackagingLevel.Each, 47)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      val totalEaches = result
        .map: r =>
          if r.packagingLevel == PackagingLevel.Each then r.quantity
          else r.quantity * hierarchy(r.packagingLevel)
        .sum
      assert(totalEaches == 47)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 2))

    it("falls through to pure Each when quantity is below all tier thresholds"):
      val line = OrderLine(skuId, PackagingLevel.Each, 3)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Each, 3)))

    it("returns results in descending packaging level order"):
      val line = OrderLine(skuId, PackagingLevel.Each, 30)
      val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 24, PackagingLevel.InnerPack -> 4)
      val result = UomExpansion(waveId, orderId, line, hierarchy)
      val levels = result.map(_.packagingLevel)
      assert(levels == List(PackagingLevel.Pallet, PackagingLevel.InnerPack, PackagingLevel.Each))
