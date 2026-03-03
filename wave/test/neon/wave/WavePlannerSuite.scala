package neon.wave

import neon.common.{OrderId, PackagingLevel, Priority, SkuId, UomHierarchy}
import neon.order.{Order, OrderLine}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class WavePlannerSuite extends AnyFunSpec:
  val sku1 = SkuId()
  val sku2 = SkuId()
  val at = Instant.now()

  describe("WavePlanner"):
    it("produces one task request per order line by default"):
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

    it("tags every task request with the source order ID"):
      val orderId = OrderId()
      val orders = List(
        Order(orderId, Priority.Normal, List(OrderLine(sku1, PackagingLevel.Each, 5)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, at)
      assert(result.taskRequests.head.orderId == orderId)

    it("tags every task request with the wave ID"):
      val orders = List(
        Order(OrderId(), Priority.Normal, List(OrderLine(sku1, PackagingLevel.Each, 10)))
      )
      val result = WavePlanner.plan(orders, OrderGrouping.Single, at)
      assert(result.taskRequests.forall(_.waveId == result.wave.id))

    it("rejects an empty order list"):
      assertThrows[IllegalArgumentException]:
        WavePlanner.plan(List.empty, OrderGrouping.Single, at)

    it("flattens lines across multiple orders"):
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

    it("emits a WaveReleased event with grouping, order IDs, and timestamp"):
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

    describe("lineResolution"):
      it("defaults to 1:1 pass-through"):
        val orders = List(
          Order(OrderId(), Priority.Normal, List(OrderLine(sku1, PackagingLevel.Each, 28)))
        )
        val result = WavePlanner.plan(orders, OrderGrouping.Single, at)
        assert(result.taskRequests.length == 1)
        assert(result.taskRequests.head.packagingLevel == PackagingLevel.Each)
        assert(result.taskRequests.head.quantity == 28)

      it("delegates to custom strategy when provided"):
        val skuId = SkuId()
        val hierarchies =
          Map(skuId -> UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6))
        val orders = List(
          Order(OrderId(), Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 28)))
        )
        val result = WavePlanner.plan(
          orders,
          OrderGrouping.Single,
          at,
          lineResolution = (waveId, orderId, line) =>
            UomExpansion(
              waveId,
              orderId,
              line,
              hierarchies.getOrElse(line.skuId, UomHierarchy.empty)
            )
        )
        assert(result.taskRequests.length == 3)
        assert(
          result.taskRequests.exists(r =>
            r.packagingLevel == PackagingLevel.Pallet && r.quantity == 1
          )
        )
