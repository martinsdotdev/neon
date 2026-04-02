import type {
  ConsolidationGroupId,
  OrderId,
  WaveId,
  WorkstationId,
} from "./ids"
import type { ConsolidationGroupStatus } from "./enums"

export interface ConsolidationGroup {
  id: ConsolidationGroupId
  status: ConsolidationGroupStatus
  waveId: WaveId
  orderIds: OrderId[]
  workstationId: WorkstationId | null
  createdAt: string
  updatedAt: string
}
