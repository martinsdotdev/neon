package neon.wave

import neon.common.{OrderId, PackagingLevel, SkuId}

case class Order(id: OrderId, lines: List[OrderLine])

case class OrderLine(skuId: SkuId, packagingLevel: PackagingLevel, quantity: Int)
