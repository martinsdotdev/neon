import type { CarrierId, OrderId, SkuId } from "./ids"
import type { PackagingLevel, Priority } from "./enums"

export interface OrderLine {
  skuId: SkuId
  packagingLevel: PackagingLevel
  quantity: number
}

export interface Order {
  id: OrderId
  priority: Priority
  lines: OrderLine[]
  carrierId: CarrierId | null
}
