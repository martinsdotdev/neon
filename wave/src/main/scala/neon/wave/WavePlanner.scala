package neon.wave

import neon.common.WaveId
import neon.order.Order
import neon.sku.Sku

import java.time.Instant

case class WavePlan(
    wave: Wave.Released,
    event: WaveEvent.WaveReleased,
    taskRequests: List[TaskRequest]
)

object WavePlanner:
  def plan(
      orders: List[Order],
      grouping: OrderGrouping,
      at: Instant,
      skus: List[Sku] = List.empty
  ): WavePlan =
    require(orders.nonEmpty, "orders must not be empty")
    val id = WaveId()
    val orderIds = orders.map(_.id)
    val planned = Wave.Planned(id, grouping, orderIds)
    val (released, event) = planned.release(at)

    val skuMap = skus.map(s => s.id -> s).toMap
    val taskRequests = for
      order <- orders
      line <- order.lines
      hierarchy = skuMap.get(line.skuId).map(_.uomHierarchy).getOrElse(Map.empty)
      req <- UomDecomposition(id, order.id, line, hierarchy)
    yield req

    WavePlan(released, event, taskRequests)
