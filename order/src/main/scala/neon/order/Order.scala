package neon.order

import neon.common.{OrderId, PackagingLevel, Priority, SkuId}

case class Order(id: OrderId, priority: Priority, lines: List[OrderLine])

case class OrderLine(skuId: SkuId, packagingLevel: PackagingLevel, quantity: Int)
