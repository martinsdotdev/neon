package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, WaveId}
import neon.order.OrderLine

object UomDecomposition:
  def apply(
      waveId: WaveId,
      orderId: OrderId,
      line: OrderLine,
      uomHierarchy: Map[PackagingLevel, Int]
  ): List[TaskRequest] =
    if line.packagingLevel != PackagingLevel.Each then
      List(TaskRequest(waveId, orderId, line.skuId, line.packagingLevel, line.quantity))
    else if uomHierarchy.isEmpty then
      List(TaskRequest(waveId, orderId, line.skuId, PackagingLevel.Each, line.quantity))
    else decompose(waveId, orderId, line.skuId, line.quantity, uomHierarchy)

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
