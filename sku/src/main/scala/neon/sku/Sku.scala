package neon.sku

import neon.common.SkuId

case class Sku(
    id: SkuId,
    code: String,
    description: String,
    lotManaged: Boolean
)
