package neon.wave

import neon.common.{OrderId, PackagingLevel, Priority, SkuId}
import neon.order.{Order, OrderLine}
import neon.sku.Sku
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class WavePlannerSuite extends AnyFunSpec:
  val sku1 = SkuId()
  val sku2 = SkuId()
  val at = Instant.now()

  describe("WavePlanner"):
    it("creates one task request per order line"):
      val orders = List(
        Order(
          OrderId(),
          Priority.Normal,
          List(
            OrderLine(sku1, PackagingLevel.Each, 5),
            OrderLine(sku2, PackagingLevel.Case, 3)
          )
        )
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, at)
      assert(result.taskRequests.length == 2)
      assert(result.taskRequests.exists(r => r.skuId == sku1 && r.quantity == 5))
      assert(result.taskRequests.exists(r => r.skuId == sku2 && r.quantity == 3))

    it("preserves order ID traceability in task requests"):
      val orderId = OrderId()
      val orders = List(
        Order(orderId, Priority.Normal, List(OrderLine(sku1, PackagingLevel.Each, 5)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, at)
      assert(result.taskRequests.head.orderId == orderId)

    it("carries wave ID in all task requests"):
      val orders = List(
        Order(OrderId(), Priority.Normal, List(OrderLine(sku1, PackagingLevel.Each, 10)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, at)
      assert(result.taskRequests.forall(_.waveId == result.wave.id))

    it("rejects empty order list"):
      assertThrows[IllegalArgumentException]:
        WavePlanner.plan(List.empty, OrderGrouping.Single, at)

    it("flattens multiple orders into individual task requests"):
      val order1 = Order(
        OrderId(),
        Priority.Normal,
        List(
          OrderLine(sku1, PackagingLevel.Each, 5),
          OrderLine(sku2, PackagingLevel.Case, 3)
        )
      )
      val order2 = Order(
        OrderId(),
        Priority.Normal,
        List(
          OrderLine(sku1, PackagingLevel.Pallet, 1)
        )
      )
      val result = WavePlanner.plan(List(order1, order2), OrderGrouping.Multi, at)
      assert(result.taskRequests.length == 3)
      assert(result.taskRequests.count(_.orderId == order1.id) == 2)
      assert(result.taskRequests.count(_.orderId == order2.id) == 1)

    it("produces a wave and event consumed by downstream policies"):
      val orderId1 = OrderId()
      val orderId2 = OrderId()
      val orders = List(
        Order(orderId1, Priority.Normal, List(OrderLine(sku1, PackagingLevel.Each, 5))),
        Order(orderId2, Priority.Normal, List(OrderLine(sku2, PackagingLevel.Case, 3)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Multi, at)
      assert(result.event.orderGrouping == OrderGrouping.Multi)
      assert(result.event.orderIds == List(orderId1, orderId2))
      assert(result.event.occurredAt == at)
      assert(result.wave.id == result.event.waveId)

    describe("UOM hierarchy decomposition"):
      it("decomposes Each line into Pallet + Case + Each when SKU has full hierarchy"):
        val skuId = SkuId()
        val orderId = OrderId()
        val sku = Sku(
          skuId,
          "SKU-1",
          "Test SKU",
          lotManaged = false,
          uomHierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        )
        val orders = List(
          Order(orderId, Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 28)))
        )
        val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
        assert(result.taskRequests.length == 3)
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1
          )
        )
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Case && r.quantity == 1
          )
        )
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Each && r.quantity == 2
          )
        )

      it("creates a single Each task for SKU without hierarchy"):
        val skuId = SkuId()
        val orderId = OrderId()
        val sku = Sku(skuId, "SKU-2", "No hierarchy", lotManaged = false)
        val orders = List(
          Order(orderId, Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 28)))
        )
        val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
        assert(result.taskRequests.length == 1)
        assert(result.taskRequests.head.packagingLevel == PackagingLevel.Each)
        assert(result.taskRequests.head.quantity == 28)

      it("decomposes with Case-only hierarchy into Case + Each"):
        val skuId = SkuId()
        val orderId = OrderId()
        val sku = Sku(
          skuId,
          "SKU-3",
          "Case only",
          lotManaged = false,
          uomHierarchy = Map(PackagingLevel.Case -> 6)
        )
        val orders = List(
          Order(orderId, Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 14)))
        )
        val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
        assert(result.taskRequests.length == 2)
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Case && r.quantity == 2
          )
        )
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Each && r.quantity == 2
          )
        )

      it("passes through non-Each order lines without decomposition"):
        val skuId = SkuId()
        val orderId = OrderId()
        val sku = Sku(
          skuId,
          "SKU-4",
          "Pallet SKU",
          lotManaged = false,
          uomHierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        )
        val orders = List(
          Order(
            orderId,
            Priority.Normal,
            List(
              OrderLine(skuId, PackagingLevel.Case, 3),
              OrderLine(skuId, PackagingLevel.Pallet, 2)
            )
          )
        )
        val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
        assert(result.taskRequests.length == 2)
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Case && r.quantity == 3
          )
        )
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Pallet && r.quantity == 2
          )
        )

      it("propagates wave ID in all decomposed task requests"):
        val skuId = SkuId()
        val orderId = OrderId()
        val sku = Sku(
          skuId,
          "SKU-5",
          "Wave ID check",
          lotManaged = false,
          uomHierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
        )
        val orders = List(
          Order(orderId, Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 28)))
        )
        val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
        assert(result.taskRequests.forall(_.waveId == result.wave.id))

      it(
        "produces exactly one Pallet task when quantity is an exact multiple — no zero-Each remainder"
      ):
        val skuId = SkuId()
        val orderId = OrderId()
        val sku = Sku(
          skuId,
          "SKU-6",
          "Exact multiple",
          lotManaged = false,
          uomHierarchy = Map(PackagingLevel.Pallet -> 20)
        )
        val orders = List(
          Order(orderId, Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 20)))
        )
        val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
        assert(result.taskRequests.length == 1)
        assert(result.taskRequests.head.packagingLevel == PackagingLevel.Pallet)
        assert(result.taskRequests.head.quantity == 1)

      it("decomposes with a four-level hierarchy including InnerPack"):
        val skuId = SkuId()
        val orderId = OrderId()
        // 1 Pallet(24) + 1 InnerPack(4) + 2 Each = 30 total
        val sku = Sku(
          skuId,
          "SKU-7",
          "Full hierarchy",
          lotManaged = false,
          uomHierarchy = Map(
            PackagingLevel.Pallet -> 24,
            PackagingLevel.InnerPack -> 4
          )
        )
        val orders = List(
          Order(orderId, Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 30)))
        )
        val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
        assert(result.taskRequests.length == 3)
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1
          )
        )
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.InnerPack && r.quantity == 1
          )
        )
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Each && r.quantity == 2
          )
        )
