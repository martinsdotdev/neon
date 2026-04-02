import type {
  HandlingUnitId,
  LocationId,
  TransportOrderId,
} from "./ids"
import type { TransportOrderStatus } from "./enums"

export interface TransportOrder {
  id: TransportOrderId
  status: TransportOrderStatus
  handlingUnitId: HandlingUnitId
  destination: LocationId
  createdAt: string
  updatedAt: string
}
