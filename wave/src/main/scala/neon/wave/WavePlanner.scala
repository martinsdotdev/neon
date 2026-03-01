package neon.wave

import neon.common.WaveId
import neon.order.Order
import java.time.Instant

case class WavePlan(
    wave: Wave.Released,
    event: WaveEvent.WaveReleased,
    taskRequests: List[TaskRequest],
    consolidationGroupRequests: List[ConsolidationGroupRequest]
)

object WavePlanner:
  def plan(
      orders: List[Order],
      grouping: OrderGrouping,
      strategy: FulfillmentStrategy,
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

    val consolidationGroupRequests = strategy match
      case FulfillmentStrategy.Direct         => Nil
      case FulfillmentStrategy.Deconsolidation =>
        List(ConsolidationGroupRequest(id, orderIds))

    WavePlan(released, event, taskRequests, consolidationGroupRequests)
