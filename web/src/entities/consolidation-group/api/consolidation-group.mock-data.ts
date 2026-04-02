import type { ConsolidationGroup } from "@/shared/types/consolidation-group"
import type {
  ConsolidationGroupId,
  OrderId,
  WaveId,
  WorkstationId,
} from "@/shared/types/ids"

export const mockConsolidationGroups: ConsolidationGroup[] =
  [
    {
      id: "0193a5b0-0007-7000-8000-000000000001" as ConsolidationGroupId,
      status: "Created",
      waveId:
        "0193a5b0-0001-7000-8000-000000000001" as WaveId,
      orderIds: [
        "0193a5b0-0002-7000-8000-000000000001" as OrderId,
        "0193a5b0-0002-7000-8000-000000000002" as OrderId,
      ],
      workstationId: null,
      createdAt: "2026-04-01T08:00:00Z",
      updatedAt: "2026-04-01T08:00:00Z",
    },
    {
      id: "0193a5b0-0007-7000-8000-000000000002" as ConsolidationGroupId,
      status: "Picked",
      waveId:
        "0193a5b0-0001-7000-8000-000000000002" as WaveId,
      orderIds: [
        "0193a5b0-0002-7000-8000-000000000003" as OrderId,
      ],
      workstationId: null,
      createdAt: "2026-04-01T07:30:00Z",
      updatedAt: "2026-04-01T08:30:00Z",
    },
    {
      id: "0193a5b0-0007-7000-8000-000000000003" as ConsolidationGroupId,
      status: "Assigned",
      waveId:
        "0193a5b0-0001-7000-8000-000000000003" as WaveId,
      orderIds: [
        "0193a5b0-0002-7000-8000-000000000004" as OrderId,
        "0193a5b0-0002-7000-8000-000000000005" as OrderId,
      ],
      workstationId:
        "0193a5b0-0008-7000-8000-000000000001" as WorkstationId,
      createdAt: "2026-04-01T06:00:00Z",
      updatedAt: "2026-04-01T08:45:00Z",
    },
    {
      id: "0193a5b0-0007-7000-8000-000000000004" as ConsolidationGroupId,
      status: "Completed",
      waveId:
        "0193a5b0-0001-7000-8000-000000000003" as WaveId,
      orderIds: [
        "0193a5b0-0002-7000-8000-000000000004" as OrderId,
      ],
      workstationId:
        "0193a5b0-0008-7000-8000-000000000002" as WorkstationId,
      createdAt: "2026-04-01T05:00:00Z",
      updatedAt: "2026-04-01T09:00:00Z",
    },
  ]
