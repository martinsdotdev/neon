import { unwrapForQuery } from "@neon/client/query"
import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface CycleCount {
  countMethod: "Blind" | "Informed"
  countType: "Planned" | "Random" | "Triggered" | "Recount"
  createdAt: string
  id: string
  skuIds: Array<string>
  state: "Created" | "InProgress" | "Completed" | "Cancelled"
  taskCount: number
  warehouseAreaId: string
}

const MOCK_CCS: Array<CycleCount> = import.meta.env.DEV
  ? [
      {
        countMethod: "Blind",
        countType: "Planned",
        createdAt: "2026-04-14T06:00:00Z",
        id: "cc001",
        skuIds: ["s001", "s002"],
        state: "InProgress",
        taskCount: 4,
        warehouseAreaId: "z001",
      },
      {
        countMethod: "Informed",
        countType: "Triggered",
        createdAt: "2026-04-13T15:00:00Z",
        id: "cc002",
        skuIds: ["s003"],
        state: "Completed",
        taskCount: 2,
        warehouseAreaId: "z002",
      },
      {
        countMethod: "Blind",
        countType: "Random",
        createdAt: "2026-04-14T10:00:00Z",
        id: "cc003",
        skuIds: ["s001", "s003", "s005"],
        state: "Created",
        taskCount: 0,
        warehouseAreaId: "z001",
      },
    ]
  : []

export const cycleCountQueries = {
  all: () =>
    queryOptions({
      queryFn: async () =>
        unwrapForQuery(apiClient.get<Array<CycleCount>>("/api/cycle-counts"), {
          fallback: import.meta.env.DEV ? MOCK_CCS : undefined,
        }),
      queryKey: ["cycle-counts"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () =>
        unwrapForQuery(apiClient.get<CycleCount>(`/api/cycle-counts/${id}`), {
          fallback: import.meta.env.DEV
            ? (MOCK_CCS.find((cc) => cc.id === id) ?? null)
            : undefined,
        }),
      queryKey: ["cycle-counts", id] as const,
    }),
}
