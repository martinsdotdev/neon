import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface ConsolidationGroup {
  createdAt: string
  id: string
  orderCount: number
  orderIds: string[]
  state: "Created" | "Picked" | "ReadyForWorkstation" | "Assigned" | "Completed" | "Cancelled"
  waveId: string
  workstationId: string | null
}

const MOCK_CGS: ConsolidationGroup[] = import.meta.env.DEV
  ? [
      { createdAt: "2026-04-14T08:10:00Z", id: "cg001", orderCount: 3, orderIds: ["o001", "o002", "o003"], state: "Assigned", waveId: "w001", workstationId: "ws001" },
      { createdAt: "2026-04-14T07:40:00Z", id: "cg002", orderCount: 1, orderIds: ["o004"], state: "Completed", waveId: "w002", workstationId: "ws002" },
      { createdAt: "2026-04-14T09:05:00Z", id: "cg003", orderCount: 2, orderIds: ["o005", "o006"], state: "Created", waveId: "w003", workstationId: null },
    ]
  : []

export const consolidationGroupQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<ConsolidationGroup[]>("/api/consolidation-groups")
        return result.unwrapOr(MOCK_CGS)
      },
      queryKey: ["consolidation-groups"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<ConsolidationGroup>(`/api/consolidation-groups/${id}`)
        return result.unwrapOr(MOCK_CGS.find((cg) => cg.id === id) ?? null)
      },
      queryKey: ["consolidation-groups", id] as const,
    }),
}
