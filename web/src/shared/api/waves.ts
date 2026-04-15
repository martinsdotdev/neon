import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface Wave {
  createdAt: string
  id: string
  orderCount: number
  orderGrouping: "Single" | "Multi"
  orderIds: string[]
  state: "Planned" | "Released" | "Completed" | "Cancelled"
}

const MOCK_WAVES: Wave[] = import.meta.env.DEV
  ? [
      { createdAt: "2026-04-14T08:00:00Z", id: "w001", orderCount: 3, orderGrouping: "Multi", orderIds: ["o001", "o002", "o003"], state: "Released" },
      { createdAt: "2026-04-14T07:30:00Z", id: "w002", orderCount: 1, orderGrouping: "Single", orderIds: ["o004"], state: "Completed" },
      { createdAt: "2026-04-14T09:00:00Z", id: "w003", orderCount: 2, orderGrouping: "Multi", orderIds: ["o005", "o006"], state: "Planned" },
      { createdAt: "2026-04-13T14:00:00Z", id: "w004", orderCount: 4, orderGrouping: "Multi", orderIds: ["o007", "o008", "o009", "o010"], state: "Cancelled" },
    ]
  : []

export const waveQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Wave[]>("/api/waves")
        return result.unwrapOr(MOCK_WAVES)
      },
      queryKey: ["waves"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Wave>(`/api/waves/${id}`)
        return result.unwrapOr(MOCK_WAVES.find((w) => w.id === id) ?? null)
      },
      queryKey: ["waves", id] as const,
    }),
}
