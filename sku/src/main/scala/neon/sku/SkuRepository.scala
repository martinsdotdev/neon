package neon.sku

import neon.common.SkuId

/** Port trait for [[Sku]] reference data queries (read-only). */
trait SkuRepository:

  /** Finds a SKU by its unique identifier.
    *
    * @param id
    *   the SKU identifier
    * @return
    *   the SKU if it exists, [[None]] otherwise
    */
  def findById(id: SkuId): Option[Sku]
