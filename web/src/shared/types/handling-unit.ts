import type { HandlingUnitId, LocationId, OrderId } from "./ids"
import type { HandlingUnitStatus, PackagingLevel } from "./enums"

export interface HandlingUnit {
  id: HandlingUnitId
  status: HandlingUnitStatus
  packagingLevel: PackagingLevel
  currentLocation: LocationId | null
  orderId: OrderId | null
}
