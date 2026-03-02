package neon.sku

import neon.common.{PackagingLevel, SkuId}

case class Sku(
    id: SkuId,
    code: String,
    description: String,
    lotManaged: Boolean,
    uomHierarchy: Map[PackagingLevel, Int] = Map.empty
)
