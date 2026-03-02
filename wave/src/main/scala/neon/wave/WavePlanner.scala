package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, WaveId}
import neon.order.{Order, OrderLine}
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
      req <- expand(id, order.id, line, skuMap.get(line.skuId))
    yield req

    WavePlan(released, event, taskRequests)

  private def expand(
      waveId: WaveId,
      orderId: OrderId,
      line: OrderLine,
      sku: Option[Sku]
  ): List[TaskRequest] =
    if line.packagingLevel != PackagingLevel.Each then
      List(TaskRequest(waveId, orderId, line.skuId, line.packagingLevel, line.quantity))
    else
      val hierarchy = sku.map(_.uomHierarchy).getOrElse(Map.empty)
      if hierarchy.isEmpty then
        List(TaskRequest(waveId, orderId, line.skuId, PackagingLevel.Each, line.quantity))
      else decompose(waveId, orderId, line.skuId, line.quantity, hierarchy)

  private def decompose(
      waveId: WaveId,
      orderId: OrderId,
      skuId: SkuId,
      quantity: Int,
      uomHierarchy: Map[PackagingLevel, Int]
  ): List[TaskRequest] =
    val levels = PackagingLevel.values.filter(uomHierarchy.contains)
    val (requests, remaining) = levels.foldLeft((List.empty[TaskRequest], quantity)):
      case ((acc, rem), level) =>
        val unitsPerLevel = uomHierarchy(level)
        val count = rem / unitsPerLevel
        if count > 0 then
          (acc :+ TaskRequest(waveId, orderId, skuId, level, count), rem - count * unitsPerLevel)
        else (acc, rem)
    if remaining > 0 then
      requests :+ TaskRequest(waveId, orderId, skuId, PackagingLevel.Each, remaining)
    else requests
