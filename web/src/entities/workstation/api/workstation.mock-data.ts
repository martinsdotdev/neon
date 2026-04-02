import type { Workstation } from "@/shared/types/workstation"
import type {
  ConsolidationGroupId,
  WorkstationId,
} from "@/shared/types/ids"

export const mockWorkstations: Workstation[] = [
  {
    id: "0193a5b0-0007-7000-8000-000000000001" as WorkstationId,
    status: "Idle",
    workstationType: "PutWall",
    slotCount: 8,
    consolidationGroupId: null,
  },
  {
    id: "0193a5b0-0007-7000-8000-000000000002" as WorkstationId,
    status: "Active",
    workstationType: "PackStation",
    slotCount: 4,
    consolidationGroupId:
      "0193a5b0-0008-7000-8000-000000000001" as ConsolidationGroupId,
  },
  {
    id: "0193a5b0-0007-7000-8000-000000000003" as WorkstationId,
    status: "Disabled",
    workstationType: "PutWall",
    slotCount: 8,
    consolidationGroupId: null,
  },
]
