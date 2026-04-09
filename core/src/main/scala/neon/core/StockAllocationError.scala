package neon.core

import neon.common.SkuId

/** Errors that can occur during stock allocation. */
sealed trait StockAllocationError

object StockAllocationError:

  /** No stock available for the requested SKU. */
  case class InsufficientStock(skuId: SkuId, requested: Int, available: Int)
      extends StockAllocationError

  /** All available stock has insufficient remaining shelf life for the minimum requirement. */
  case class InsufficientShelfLife(skuId: SkuId, requiredDays: Int, availableDays: Int)
      extends StockAllocationError
