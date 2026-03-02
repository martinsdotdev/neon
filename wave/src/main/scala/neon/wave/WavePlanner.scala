package neon.wave

import neon.common.WaveId
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
      at: Instant
  ): WavePlan =
    val id = WaveId()
    val orderIds = orders.map(_.id)
    val planned = Wave.Planned(id, grouping, orderIds)
    val (released, event) = planned.release(at)

    val taskRequests = for
      order <- orders
      line  <- order.lines
    yield TaskRequest(id, order.id, line.skuId, line.packagingLevel, line.quantity)

    WavePlan(released, event, taskRequests)
