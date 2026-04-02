import type { Wave } from "@/shared/types/wave"
import type { OrderId, WaveId } from "@/shared/types/ids"

export const mockWaves: Wave[] = [
  {
    id: "0193a5b0-0001-7000-8000-000000000001" as WaveId,
    status: "Planned",
    orderGrouping: "Multi",
    orderIds: [
      "0193a5b0-0002-7000-8000-000000000001" as OrderId,
      "0193a5b0-0002-7000-8000-000000000002" as OrderId,
    ],
    createdAt: "2026-04-01T08:00:00Z",
    updatedAt: "2026-04-01T08:00:00Z",
  },
  {
    id: "0193a5b0-0001-7000-8000-000000000002" as WaveId,
    status: "Released",
    orderGrouping: "Single",
    orderIds: [
      "0193a5b0-0002-7000-8000-000000000003" as OrderId,
    ],
    createdAt: "2026-04-01T07:30:00Z",
    updatedAt: "2026-04-01T08:15:00Z",
  },
  {
    id: "0193a5b0-0001-7000-8000-000000000003" as WaveId,
    status: "Completed",
    orderGrouping: "Multi",
    orderIds: [
      "0193a5b0-0002-7000-8000-000000000004" as OrderId,
      "0193a5b0-0002-7000-8000-000000000005" as OrderId,
    ],
    createdAt: "2026-04-01T06:00:00Z",
    updatedAt: "2026-04-01T09:00:00Z",
  },
  {
    id: "0193a5b0-0001-7000-8000-000000000004" as WaveId,
    status: "Cancelled",
    orderGrouping: "Single",
    orderIds: [
      "0193a5b0-0002-7000-8000-000000000006" as OrderId,
    ],
    createdAt: "2026-03-31T14:00:00Z",
    updatedAt: "2026-03-31T14:30:00Z",
  },
]
