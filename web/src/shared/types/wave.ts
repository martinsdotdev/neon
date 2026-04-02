import type { OrderId, WaveId } from "./ids"
import type { OrderGrouping, WaveStatus } from "./enums"

export interface Wave {
  id: WaveId
  status: WaveStatus
  orderGrouping: OrderGrouping
  orderIds: OrderId[]
  createdAt: string
  updatedAt: string
}
