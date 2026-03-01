package neon.wave

import neon.common.WaveId

case class WavePlan(
    wave: Wave.Released,
    event: WaveEvent.WaveReleased,
    taskRequests: List[TaskRequest]
)

object WavePlanner:
  def plan(orders: List[Order], grouping: OrderGrouping): WavePlan =
    val id = WaveId()
    val orderIds = orders.map(_.id)
    val planned = Wave.Planned(id, grouping, orderIds)
    val (released, event) = planned.release()
    val taskRequests = for
      order <- orders
      line  <- order.lines
    yield TaskRequest(id, line.skuId, line.packagingLevel, line.quantity)
    WavePlan(released, event, taskRequests)
