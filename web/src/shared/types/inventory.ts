import type { InventoryId, LocationId, SkuId } from "./ids"
import type { PackagingLevel } from "./enums"

export interface Inventory {
  id: InventoryId
  locationId: LocationId
  skuId: SkuId
  packagingLevel: PackagingLevel
  lot: string | null
  onHand: number
  reserved: number
  available: number
}
