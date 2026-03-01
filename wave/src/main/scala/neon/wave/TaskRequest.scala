package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId, WaveId}

case class TaskRequest(
    waveId: WaveId,
    orderId: OrderId,
    skuId: SkuId,
    packagingLevel: PackagingLevel,
    quantity: Int
)
