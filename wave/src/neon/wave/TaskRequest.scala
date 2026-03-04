package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, WaveId}

/** A request to create a warehouse task, produced by [[WavePlanner]] during wave planning.
  *
  * Each task request maps to exactly one SKU at a specific [[PackagingLevel]]. The downstream task
  * creation layer converts these into concrete [[neon.task.Task]] instances.
  *
  * @param waveId
  *   the wave this request belongs to
  * @param orderId
  *   the order this request fulfills
  * @param skuId
  *   the SKU to pick or move
  * @param packagingLevel
  *   the packaging level of the requested quantity
  * @param quantity
  *   the number of units to handle at the given packaging level
  */
case class TaskRequest(
    waveId: WaveId,
    orderId: OrderId,
    skuId: SkuId,
    packagingLevel: PackagingLevel,
    quantity: Int
)
