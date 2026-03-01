package neon.wave

import neon.common.{PackagingLevel, SkuId}

case class TaskRequest(
    waveId: WaveId,
    skuId: SkuId,
    packagingLevel: PackagingLevel,
    quantity: Int
)
