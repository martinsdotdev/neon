package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId}
import org.scalatest.funspec.AnyFunSpec

class WavePlannerSuite extends AnyFunSpec:
  val sku1 = SkuId()
  val sku2 = SkuId()

  describe("WavePlanner"):
    it("releases a wave with the given orders"):
      val orders = List(
        Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 5)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single)
      assert(result.event.orderIds == orders.map(_.id))

    it("creates a task request per order line"):
      val orders = List(
        Order(
          OrderId(),
          List(
            OrderLine(sku1, PackagingLevel.Each, 5),
            OrderLine(sku2, PackagingLevel.Case, 3)
          )
        )
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single)
      assert(result.taskRequests.length == 2)
      assert(result.taskRequests.exists(r => r.skuId == sku1 && r.quantity == 5))
      assert(result.taskRequests.exists(r => r.skuId == sku2 && r.quantity == 3))

    it("carries the wave ID in task requests for traceability"):
      val orders = List(
        Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 10)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single)
      assert(result.taskRequests.forall(_.waveId == result.wave.id))

    it("groups multiple orders into one wave"):
      val order1 = Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 5)))
      val order2 = Order(OrderId(), List(OrderLine(sku2, PackagingLevel.Case, 3)))
      val result = WavePlanner.plan(List(order1, order2), OrderGrouping.Multi)
      assert(result.event.orderIds.length == 2)
      assert(result.taskRequests.length == 2)
