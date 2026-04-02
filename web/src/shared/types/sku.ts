import type { SkuId } from "./ids"
import type { PackagingLevel } from "./enums"

export type UomHierarchy = Partial<Record<PackagingLevel, number>>

export interface Sku {
  id: SkuId
  code: string
  description: string
  lotManaged: boolean
  uomHierarchy: UomHierarchy
}
