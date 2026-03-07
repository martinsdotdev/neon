package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, UomHierarchy, WaveId}
import neon.order.OrderLine

/** Expands one order line into task requests at the coarsest possible packaging levels. Converts
  * the line quantity to eaches, then greedily assigns from the largest tier down. Lines whose level
  * is absent from the hierarchy pass through 1:1.
  */
object UomExpansion:
  /** Expands a single [[OrderLine]] into task requests at the coarsest possible packaging levels.
    *
    * Converts the line quantity to eaches using the hierarchy, then greedily assigns from the
    * largest tier down. If the line's [[PackagingLevel]] is absent from the hierarchy, or the
    * hierarchy is empty, the line passes through as a single [[TaskRequest]] unchanged.
    *
    * @param waveId
    *   the wave this expansion belongs to
    * @param orderId
    *   the order the line belongs to
    * @param line
    *   the order line to expand
    * @param uomHierarchy
    *   the packaging-level-to-eaches mapping for the SKU
    * @return
    *   one or more task requests covering the full line quantity
    */
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
        expand(waveId, orderId, line.skuId, eaches, uomHierarchy)
      case _ =>
        List(TaskRequest(waveId, orderId, line.skuId, line.packagingLevel, line.quantity))

  /** Greedily decomposes an eaches quantity into task requests from the largest packaging level
    * down. Any remainder that does not fit the smallest tier produces a final
    * [[PackagingLevel.Each]] request.
    *
    * @param waveId
    *   the wave identifier
    * @param orderId
    *   the order identifier
    * @param skuId
    *   the SKU identifier
    * @param quantity
    *   the total quantity in eaches to decompose
    * @param uomHierarchy
    *   the packaging-level-to-eaches mapping
    * @return
    *   task requests covering the full quantity
    */
  private def expand(
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
