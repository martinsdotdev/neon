package neon.wave

import neon.common.{OrderId, WaveId}
import neon.order.{Order, OrderLine}

import java.time.Instant

case class WavePlan(
    wave: Wave.Released,
    event: WaveEvent.WaveReleased,
    taskRequests: List[TaskRequest]
)

object WavePlanner:
  private val passThrough: (WaveId, OrderId, OrderLine) => List[TaskRequest] =
    (waveId, orderId, line) =>
      List(TaskRequest(waveId, orderId, line.skuId, line.packagingLevel, line.quantity))

  def plan(
      orders: List[Order],
      grouping: OrderGrouping,
      at: Instant,
      lineResolution: (WaveId, OrderId, OrderLine) => List[TaskRequest] = passThrough
  ): WavePlan =
    require(orders.nonEmpty, "orders must not be empty")
    val id = WaveId()
    val orderIds = orders.map(_.id)
    val planned = Wave.Planned(id, grouping, orderIds)
    val (released, event) = planned.release(at)

    val taskRequests = for
      order <- orders
      line <- order.lines
      req <- lineResolution(id, order.id, line)
    yield req

    WavePlan(released, event, taskRequests)
