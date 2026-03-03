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

    it("delegates UOM decomposition when SKU hierarchy is provided"):
      val skuId = SkuId()
      val sku = Sku(
        skuId,
        "SKU-H",
        "Hierarchy SKU",
        lotManaged = false,
        uomHierarchy = Map(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      )
      val orders = List(
        Order(OrderId(), Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 28)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
      assert(result.taskRequests.length == 3)
      assert(
        result.taskRequests.exists(r =>
          r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1
        )
      )

    it("treats unknown SKU as having no hierarchy"):
      val knownSku = SkuId()
      val unknownSku = SkuId()
      val sku = Sku(
        knownSku,
        "SKU-K",
        "Known SKU",
        lotManaged = false,
        uomHierarchy = Map(PackagingLevel.Pallet -> 20)
      )
      val orders = List(
        Order(
          OrderId(),
          Priority.Normal,
          List(OrderLine(unknownSku, PackagingLevel.Each, 15))
        )
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, at, List(sku))
      assert(result.taskRequests.length == 1)
      assert(result.taskRequests.head.packagingLevel == PackagingLevel.Each)
      assert(result.taskRequests.head.quantity == 15)
