package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId}
import neon.order.{Order, OrderLine}
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
          List(
            OrderLine(sku1, PackagingLevel.Each, 5),
            OrderLine(sku2, PackagingLevel.Case, 3)
          )
        )
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, FulfillmentStrategy.Direct, at)
      assert(result.taskRequests.length == 2)
      assert(result.taskRequests.exists(r => r.skuId == sku1 && r.quantity == 5))
      assert(result.taskRequests.exists(r => r.skuId == sku2 && r.quantity == 3))

    it("preserves order ID traceability in task requests"):
      val orderId = OrderId()
      val orders = List(
        Order(orderId, List(OrderLine(sku1, PackagingLevel.Each, 5)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, FulfillmentStrategy.Direct, at)
      assert(result.taskRequests.head.orderId == orderId)

    it("carries wave ID in all task requests"):
      val orders = List(
        Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 10)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, FulfillmentStrategy.Direct, at)
      assert(result.taskRequests.forall(_.waveId == result.wave.id))

    it("Direct strategy produces no consolidation group requests"):
      val orders = List(
        Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 5)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, FulfillmentStrategy.Direct, at)
      assert(result.consolidationGroupRequests.isEmpty)

    it("Deconsolidation strategy produces a consolidation group request with all order IDs"):
      val id1 = OrderId()
      val id2 = OrderId()
      val orders = List(
        Order(id1, List(OrderLine(sku1, PackagingLevel.Each, 5))),
        Order(id2, List(OrderLine(sku2, PackagingLevel.Case, 3)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Multi, FulfillmentStrategy.Deconsolidation, at)
      assert(result.consolidationGroupRequests.length == 1)
      assert(result.consolidationGroupRequests.head.orderIds == List(id1, id2))

    it("handles multiple orders with multiple lines"):
      val order1 = Order(OrderId(), List(
        OrderLine(sku1, PackagingLevel.Each, 5),
        OrderLine(sku2, PackagingLevel.Case, 3)
      ))
      val order2 = Order(OrderId(), List(
        OrderLine(sku1, PackagingLevel.Pallet, 1)
      ))
      val result = WavePlanner.plan(List(order1, order2), OrderGrouping.Multi, FulfillmentStrategy.Direct, at)
      assert(result.taskRequests.length == 3)
      assert(result.taskRequests.count(_.orderId == order1.id) == 2)
      assert(result.taskRequests.count(_.orderId == order2.id) == 1)

    it("consolidation group request carries the wave ID"):
      val orders = List(
        Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 5)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, FulfillmentStrategy.Deconsolidation, at)
      assert(result.consolidationGroupRequests.head.waveId == result.wave.id)
