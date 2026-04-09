package neon.goodsreceipt

import neon.common.{ContainerId, LotAttributes, PackagingLevel, SkuId}

/** A single line item within a goods receipt, representing received goods.
  *
  * @param skuId
  *   the stock-keeping unit received
  * @param quantity
  *   the quantity received (must be positive)
  * @param packagingLevel
  *   the packaging level of the received goods
  * @param lotAttributes
  *   lot tracking attributes for the received goods
  * @param targetContainerId
  *   optional container where the goods were placed
  */
case class ReceivedLine(
    skuId: SkuId,
    quantity: Int,
    packagingLevel: PackagingLevel,
    lotAttributes: LotAttributes,
    targetContainerId: Option[ContainerId]
)
