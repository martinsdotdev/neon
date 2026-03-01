package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId}

class WavePlannerSuite extends munit.FunSuite:
  val sku1 = SkuId()
  val sku2 = SkuId()

  test("planning a wave releases it with the given orders"):
    val orders = List(
      Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 5)))
    )
    val result = WavePlanner.plan(orders, OrderGrouping.Single)
    assertEquals(result.event.orderIds, orders.map(_.id))

  test("each order line becomes a task request"):
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
    assertEquals(result.taskRequests.length, 2)
    assert(result.taskRequests.exists(r => r.skuId == sku1 && r.quantity == 5))
    assert(result.taskRequests.exists(r => r.skuId == sku2 && r.quantity == 3))

  test("task requests carry the wave ID for traceability"):
    val orders = List(
      Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 10)))
    )
    val result = WavePlanner.plan(orders, OrderGrouping.Single)
    assert(result.taskRequests.forall(_.waveId == result.wave.id))

  test("a multi-order wave groups all orders into one wave"):
    val order1 = Order(OrderId(), List(OrderLine(sku1, PackagingLevel.Each, 5)))
    val order2 = Order(OrderId(), List(OrderLine(sku2, PackagingLevel.Case, 3)))
    val result = WavePlanner.plan(List(order1, order2), OrderGrouping.Multi)
    assertEquals(result.event.orderIds.length, 2)
    assertEquals(result.taskRequests.length, 2)
