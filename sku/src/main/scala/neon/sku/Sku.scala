package neon.sku

import neon.common.{SkuId, UomHierarchy}

case class Sku(
    id: SkuId,
    code: String,
    description: String,
    lotManaged: Boolean,
    uomHierarchy: UomHierarchy = UomHierarchy.empty
)
