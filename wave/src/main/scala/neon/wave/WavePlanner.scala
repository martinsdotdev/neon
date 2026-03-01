package neon.wave

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
    val taskRequests = orders.flatMap: order =>
      order.lines.map: line =>
        TaskRequest(id, line.skuId, line.packagingLevel, line.quantity)
    WavePlan(released, event, taskRequests)
