package neon.wave

import neon.common.{SkuId, UomHierarchy, WaveId}
import neon.order.Order

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
      uomHierarchies: Map[SkuId, UomHierarchy] = Map.empty
  ): WavePlan =
    require(orders.nonEmpty, "orders must not be empty")
    val id = WaveId()
    val orderIds = orders.map(_.id)
    val planned = Wave.Planned(id, grouping, orderIds)
    val (released, event) = planned.release(at)

    val taskRequests = for
      order <- orders
      line <- order.lines
      hierarchy = uomHierarchies.getOrElse(line.skuId, UomHierarchy.empty)
      req <- UomExpansion(id, order.id, line, hierarchy)
    yield req

    WavePlan(released, event, taskRequests)
