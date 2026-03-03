package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, WaveId}
import neon.order.OrderLine
import org.scalatest.funspec.AnyFunSpec

class UomDecompositionSuite extends AnyFunSpec:
  val waveId = WaveId()
  val orderId = OrderId()
  val skuId = SkuId()

  describe("UomDecomposition"):
    it("decomposes Each line into Pallet + Case + Each when SKU has full hierarchy"):
      val line = OrderLine(skuId, PackagingLevel.Each, 28)
      val hierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomDecomposition(waveId, orderId, line, hierarchy)
      assert(result.length == 3)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Case && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

    it("creates a single Each task for empty hierarchy"):
      val line = OrderLine(skuId, PackagingLevel.Each, 28)
      val result = UomDecomposition(waveId, orderId, line, Map.empty)
      assert(result.length == 1)
      assert(result.head.packagingLevel == PackagingLevel.Each)
      assert(result.head.quantity == 28)

    it("decomposes with Case-only hierarchy into Case + Each"):
      val line = OrderLine(skuId, PackagingLevel.Each, 14)
      val hierarchy = Map(PackagingLevel.Case -> 6)
      val result = UomDecomposition(waveId, orderId, line, hierarchy)
      assert(result.length == 2)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Case && r.quantity == 2))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

    it("passes through non-Each order lines without decomposition"):
      val caseLine = OrderLine(skuId, PackagingLevel.Case, 3)
      val palletLine = OrderLine(skuId, PackagingLevel.Pallet, 2)
      val hierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val caseResult = UomDecomposition(waveId, orderId, caseLine, hierarchy)
      val palletResult = UomDecomposition(waveId, orderId, palletLine, hierarchy)
      assert(caseResult == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Case, 3)))
      assert(palletResult == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Pallet, 2)))

    it("propagates wave ID and order ID in all decomposed task requests"):
      val line = OrderLine(skuId, PackagingLevel.Each, 28)
      val hierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomDecomposition(waveId, orderId, line, hierarchy)
      assert(result.forall(_.waveId == waveId))
      assert(result.forall(_.orderId == orderId))

    it("produces exactly one Pallet task when quantity is an exact multiple"):
      val line = OrderLine(skuId, PackagingLevel.Each, 20)
      val hierarchy = Map(PackagingLevel.Pallet -> 20)
      val result = UomDecomposition(waveId, orderId, line, hierarchy)
      assert(result.length == 1)
      assert(result.head.packagingLevel == PackagingLevel.Pallet)
      assert(result.head.quantity == 1)

    it("decomposes with a four-level hierarchy including InnerPack"):
      val line = OrderLine(skuId, PackagingLevel.Each, 30)
      val hierarchy = Map(PackagingLevel.Pallet -> 24, PackagingLevel.InnerPack -> 4)
      val result = UomDecomposition(waveId, orderId, line, hierarchy)
      assert(result.length == 3)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.InnerPack && r.quantity == 1))
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Each && r.quantity == 2))

    it("conserves total eaches across all decomposed tiers"):
      val line = OrderLine(skuId, PackagingLevel.Each, 47)
      val hierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomDecomposition(waveId, orderId, line, hierarchy)
      val totalEaches = result
        .map: r =>
          if r.packagingLevel == PackagingLevel.Each then r.quantity
          else r.quantity * hierarchy(r.packagingLevel)
        .sum
      assert(totalEaches == 47)
      assert(result.exists(r => r.packagingLevel == PackagingLevel.Pallet && r.quantity == 2))

    it("falls through to pure Each when quantity is below all tier thresholds"):
      val line = OrderLine(skuId, PackagingLevel.Each, 3)
      val hierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      val result = UomDecomposition(waveId, orderId, line, hierarchy)
      assert(result == List(TaskRequest(waveId, orderId, skuId, PackagingLevel.Each, 3)))

    it("returns results in descending packaging level order"):
      val line = OrderLine(skuId, PackagingLevel.Each, 30)
      val hierarchy = Map(PackagingLevel.Pallet -> 24, PackagingLevel.InnerPack -> 4)
      val result = UomDecomposition(waveId, orderId, line, hierarchy)
      val levels = result.map(_.packagingLevel)
      assert(levels == List(PackagingLevel.Pallet, PackagingLevel.InnerPack, PackagingLevel.Each))
