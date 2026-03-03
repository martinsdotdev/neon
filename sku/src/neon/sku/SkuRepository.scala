package neon.sku

import neon.common.SkuId

/** Port trait for SKU reference data queries (read-only). */
trait SkuRepository:
  def findById(id: SkuId): Option[Sku]
