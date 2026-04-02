import type {
  HandlingUnitId,
  OrderId,
  SlotId,
  WorkstationId,
} from "./ids"
import type { SlotStatus } from "./enums"

export interface Slot {
  id: SlotId
  status: SlotStatus
  workstationId: WorkstationId
  orderId: OrderId | null
  handlingUnitId: HandlingUnitId | null
}
