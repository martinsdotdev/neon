package neon.sku

import neon.common.{SkuId, UomHierarchy}

/** Stock-keeping unit reference data — the catalog entry for a product.
  *
  * @param id
  *   the unique SKU identifier
  * @param code
  *   human-readable SKU code (e.g. product barcode)
  * @param description
  *   descriptive name of the product
  * @param lotManaged
  *   whether this SKU requires lot tracking on inventory
  * @param uomHierarchy
  *   unit-of-measure hierarchy mapping packaging levels to eaches
  */
case class Sku(
    id: SkuId,
    code: String,
    description: String,
    lotManaged: Boolean,
    uomHierarchy: UomHierarchy = UomHierarchy.empty
)
