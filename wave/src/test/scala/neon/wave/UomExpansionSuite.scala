package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, UomHierarchy, WaveId}
import neon.order.OrderLine
import org.scalatest.funspec.AnyFunSpec

class UomExpansionSuite extends AnyFunSpec:
  val waveId = WaveId()
  val orderId = OrderId()
  val skuId = SkuId()

  describe("UomExpansion"):
    describe("Each-level input"):
      it("splits into Pallet + Case + Each using greedy top-down"):
        val line = OrderLine(skuId, PackagingLevel.Each, 28)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result.length == 3)
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1))
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Case && r.quantity == 1))
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

      it("produces a single Each request when hierarchy is empty"):
        val line = OrderLine(skuId, PackagingLevel.Each, 28)
        val result = UomExpansion(waveId, orderId, line, UomHierarchy.empty)
        assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Each, 28)))

      it("uses only tiers present in hierarchy"):
        val line = OrderLine(skuId, PackagingLevel.Each, 14)
        val hierarchy = UomHierarchy(PackagingLevel.Case -> 6)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result.length == 2)
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Case && r.quantity == 2))
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

      it("produces exact Pallet with no remainder when quantity is a multiple"):
        val line = OrderLine(skuId, PackagingLevel.Each, 20)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Pallet, 1)))

      it("falls through to pure Each when below all tier thresholds"):
        val line = OrderLine(skuId, PackagingLevel.Each, 3)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Each, 3)))

      it("handles non-contiguous hierarchy levels"):
        val line = OrderLine(skuId, PackagingLevel.Each, 30)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 24, PackagingLevel.InnerPack -> 4)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result.length == 3)
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1))
        assert(result.exists(r => r.packagingLevel == PackagingLevel.InnerPack && r.quantity == 1))
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

    describe("non-Each input"):
      it("converts Cases to eaches then expands to coarser tiers"):
        val line = OrderLine(skuId, PackagingLevel.Case, 10)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        // 10 Cases = 60 eaches → Pallet(3) = 60 eaches
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Pallet, 3)))

      it("produces mixed tiers from non-Each input"):
        val line = OrderLine(skuId, PackagingLevel.Case, 5)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        // 5 Cases = 30 eaches → Pallet(1)=20 + Case(1)=6 + Each(4)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result.length == 3)
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1))
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Case && r.quantity == 1))
        assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 4))

      it("stays at same level when quantity is too small for coarser tiers"):
        val line = OrderLine(skuId, PackagingLevel.Case, 3)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        // 3 Cases = 18 eaches; Pallet=20 doesn't fit → Case(3)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Case, 3)))

      it("passes through when level is absent from hierarchy"):
        val line = OrderLine(skuId, PackagingLevel.InnerPack, 3)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.InnerPack, 3)))

    describe("invariants"):
      it("conserves total eaches across all tiers"):
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        def totalEaches(requests: List[TaskRequest]): Int =
          requests
            .map: r =>
              if r.packagingLevel == PackagingLevel.Each then r.quantity
              else r.quantity * hierarchy(r.packagingLevel)
            .sum

        // Each input
        val eachLine = OrderLine(skuId, PackagingLevel.Each, 47)
        assert(totalEaches(UomExpansion(waveId, orderId, eachLine, hierarchy)) == 47)

        // Non-Each input
        val caseLine = OrderLine(skuId, PackagingLevel.Case, 10)
        assert(totalEaches(UomExpansion(waveId, orderId, caseLine, hierarchy)) == 60)

      it("produces results in descending packaging level order"):
        val line = OrderLine(skuId, PackagingLevel.Each, 30)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 24, PackagingLevel.InnerPack -> 4)
        val levels = UomExpansion(waveId, orderId, line, hierarchy).map(_.packagingLevel)
        assert(levels == List(PackagingLevel.Pallet, PackagingLevel.InnerPack, PackagingLevel.Each))

      it("carries wave ID and order ID in every request"):
        val line = OrderLine(skuId, PackagingLevel.Each, 28)
        val hierarchy = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        val result = UomExpansion(waveId, orderId, line, hierarchy)
        assert(result.forall(_.waveId == waveId))
        assert(result.forall(_.orderId == orderId))
