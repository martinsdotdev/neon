import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface Delivery {
  createdAt: string
  expectedQuantity: number
  id: string
  lot: string | null
  packagingLevel: "Pallet" | "Case" | "InnerPack" | "Each"
  receivedQuantity: number
  rejectedQuantity: number
  skuId: string
  state: "Expected" | "PartiallyReceived" | "Received" | "Closed"
}

const MOCK_DELIVERIES: Array<Delivery> = import.meta.env.DEV
  ? [
      {
        createdAt: "2026-04-14T06:00:00Z",
        expectedQuantity: 100,
        id: "d001",
        lot: "LOT-2026-A",
        packagingLevel: "Case",
        receivedQuantity: 0,
        rejectedQuantity: 0,
        skuId: "s001",
        state: "Expected",
      },
      {
        createdAt: "2026-04-13T10:00:00Z",
        expectedQuantity: 50,
        id: "d002",
        lot: null,
        packagingLevel: "Pallet",
        receivedQuantity: 30,
        rejectedQuantity: 2,
        skuId: "s003",
        state: "PartiallyReceived",
      },
      {
        createdAt: "2026-04-12T08:00:00Z",
        expectedQuantity: 200,
        id: "d003",
        lot: "LOT-2026-B",
        packagingLevel: "Each",
        receivedQuantity: 200,
        rejectedQuantity: 0,
        skuId: "s002",
        state: "Received",
      },
    ]
  : []

export const deliveryQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Array<Delivery>>(
          "/api/inbound/deliveries"
        )
        return result.unwrapOr(MOCK_DELIVERIES)
      },
      queryKey: ["deliveries"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Delivery>(
          `/api/inbound/deliveries/${id}`
        )
        return result.unwrapOr(MOCK_DELIVERIES.find((d) => d.id === id) ?? null)
      },
      queryKey: ["deliveries", id] as const,
    }),
}
