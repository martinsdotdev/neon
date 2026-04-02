import type { ConsolidationGroupId, WorkstationId } from "./ids"
import type { WorkstationStatus, WorkstationType } from "./enums"

export interface Workstation {
  id: WorkstationId
  status: WorkstationStatus
  workstationType: WorkstationType
  slotCount: number
  consolidationGroupId: ConsolidationGroupId | null
}
