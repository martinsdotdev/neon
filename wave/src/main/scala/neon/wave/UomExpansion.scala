package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, UomHierarchy, WaveId}
import neon.order.OrderLine

object UomExpansion:
  def apply(
      waveId: WaveId,
      orderId: OrderId,
      line: OrderLine,
      uomHierarchy: UomHierarchy
  ): List[TaskRequest] =
    val eachesOpt = line.packagingLevel match
      case PackagingLevel.Each => Some(line.quantity)
      case level               => uomHierarchy.get(level).map(_ * line.quantity)
    eachesOpt match
      case Some(eaches) if uomHierarchy.nonEmpty =>
        decompose(waveId, orderId, line.skuId, eaches, uomHierarchy)
      case _ =>
        List(TaskRequest(waveId, orderId, line.skuId, line.packagingLevel, line.quantity))

  private def decompose(
      waveId: WaveId,
      orderId: OrderId,
      skuId: SkuId,
      quantity: Int,
      uomHierarchy: UomHierarchy
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
