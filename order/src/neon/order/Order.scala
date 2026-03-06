package neon.order

import neon.common.{CarrierId, OrderId, PackagingLevel, Priority, SkuId}

/** A customer order consisting of one or more lines to be fulfilled.
  *
  * @param id
  *   the unique order identifier
  * @param priority
  *   fulfillment priority
  * @param lines
  *   the line items to pick
  * @param carrierId
  *   optional carrier associated with this order for dispatch validation
  */
case class Order(
    id: OrderId,
    priority: Priority,
    lines: List[OrderLine],
    carrierId: Option[CarrierId] = None
)

/** A single line within an [[Order]], specifying a SKU and quantity to fulfill.
  *
  * @param skuId
  *   the stock-keeping unit to pick
  * @param packagingLevel
  *   the packaging level of the requested quantity
  * @param quantity
  *   the number of units at the given packaging level
  */
case class OrderLine(skuId: SkuId, packagingLevel: PackagingLevel, quantity: Int)
