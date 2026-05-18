import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"
import type { Workstation } from "@neon/domain/workstation"

export type { Workstation }

const MOCK_WS: Array<Workstation> = import.meta.env.DEV
  ? [
      {
        createdAt: "2026-04-10T10:00:00Z",
        id: "ws001",
        mode: "Picking",
        slotCount: 12,
        state: "Active",
        workstationType: "PutWall",
      },
      {
        createdAt: "2026-04-10T10:00:00Z",
        id: "ws002",
        mode: "Picking",
        slotCount: 8,
        state: "Idle",
        workstationType: "PackStation",
      },
      {
        createdAt: "2026-04-10T10:00:00Z",
        id: "ws003",
        mode: "Receiving",
        slotCount: 6,
        state: "Disabled",
        workstationType: "PutWall",
      },
    ]
  : []

export const workstationQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result =
          await apiClient.get<Array<Workstation>>("/api/workstations")
        return result.unwrapOr(MOCK_WS)
      },
      queryKey: ["workstations"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Workstation>(
          `/api/workstations/${id}`
        )
        return result.unwrapOr(MOCK_WS.find((ws) => ws.id === id) ?? null)
      },
      queryKey: ["workstations", id] as const,
    }),
}
