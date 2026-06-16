import { unwrapForQuery } from "@neon/client/query"
import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface StockPosition {
  availableQuantity: number
  blockedQuantity: number
  id: string
  lot: string | null
  onHandQuantity: number
  skuId: string
  warehouseAreaId: string
}

const MOCK_SPS: Array<StockPosition> = import.meta.env.DEV
  ? [
      {
        availableQuantity: 80,
        blockedQuantity: 0,
        id: "sp001",
        lot: null,
        onHandQuantity: 80,
        skuId: "s001",
        warehouseAreaId: "z001",
      },
      {
        availableQuantity: 45,
        blockedQuantity: 5,
        id: "sp002",
        lot: "LOT-2026-B",
        onHandQuantity: 50,
        skuId: "s002",
        warehouseAreaId: "z001",
      },
      {
        availableQuantity: 0,
        blockedQuantity: 20,
        id: "sp003",
        lot: null,
        onHandQuantity: 20,
        skuId: "s003",
        warehouseAreaId: "z002",
      },
    ]
  : []

export const stockPositionQueries = {
  all: () =>
    queryOptions({
      queryFn: async () =>
        unwrapForQuery(
          apiClient.get<Array<StockPosition>>("/api/stock-positions"),
          {
            fallback: import.meta.env.DEV ? MOCK_SPS : undefined,
          }
        ),
      queryKey: ["stock-positions"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () =>
        unwrapForQuery(
          apiClient.get<StockPosition>(`/api/stock-positions/${id}`),
          {
            fallback: import.meta.env.DEV
              ? (MOCK_SPS.find((sp) => sp.id === id) ?? null)
              : undefined,
          }
        ),
      queryKey: ["stock-positions", id] as const,
    }),
}
