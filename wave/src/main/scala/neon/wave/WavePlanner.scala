package neon.wave

import neon.common.{OrderId, WaveId}
import neon.order.{Order, OrderLine}

import java.time.Instant

/** Output of [[WavePlanner.plan]], bundling the released wave, its domain event, and the resolved
  * task requests.
  *
  * @param wave
  *   the released wave state
  * @param event
  *   the domain event recording the release
  * @param taskRequests
  *   the task requests produced by line resolution
  */
case class WavePlan(
    wave: Wave.Released,
    event: WaveEvent.WaveReleased,
    taskRequests: List[TaskRequest]
)

/** Creates a released wave from a list of orders, producing task requests via a pluggable
  * `lineResolution` strategy. Defaults to 1:1 pass-through; callers inject UomExpansion or other
  * strategies as needed.
  */
object WavePlanner:
  private val passThrough: (WaveId, OrderId, OrderLine) => List[TaskRequest] =
    (waveId, orderId, line) =>
      List(TaskRequest(waveId, orderId, line.skuId, line.packagingLevel, line.quantity))

  /** Plans and releases a wave in a single step, resolving order lines into task requests.
    *
    * Creates a [[Wave.Planned]], immediately releases it, and expands every [[OrderLine]] into one
    * or more [[TaskRequest]]s via the provided `lineResolution` strategy. The default strategy
    * passes each line through 1:1; callers inject [[UomExpansion]] or other strategies as needed.
    *
    * @param orders
    *   the orders to include in the wave (must be non-empty)
    * @param grouping
    *   the grouping strategy for consolidation
    * @param at
    *   instant of planning and release
    * @param lineResolution
    *   strategy for resolving each order line to task requests
    * @return
    *   a [[WavePlan]] containing the released wave, its event, and all task requests
    */
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
