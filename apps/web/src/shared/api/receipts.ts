import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface ReceiptLine {
  lot: string | null
  packagingLevel: "Pallet" | "Case" | "InnerPack" | "Each"
  quantity: number
  skuId: string
}

export interface Receipt {
  createdAt: string
  deliveryId: string
  id: string
  lines: Array<ReceiptLine>
  state: "Open" | "Confirmed"
}

const MOCK_RECEIPTS: Array<Receipt> = import.meta.env.DEV
  ? [
      {
        createdAt: "2026-04-13T11:00:00Z",
        deliveryId: "d002",
        id: "r001",
        lines: [
          { lot: null, packagingLevel: "Pallet", quantity: 30, skuId: "s003" },
        ],
        state: "Confirmed",
      },
      {
        createdAt: "2026-04-14T07:00:00Z",
        deliveryId: "d001",
        id: "r002",
        lines: [],
        state: "Open",
      },
    ]
  : []

export const receiptQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Array<Receipt>>("/api/inbound/receipts")
        return result.unwrapOr(MOCK_RECEIPTS)
      },
      queryKey: ["receipts"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Receipt>(
          `/api/inbound/receipts/${id}`
        )
        return result.unwrapOr(MOCK_RECEIPTS.find((r) => r.id === id) ?? null)
      },
      queryKey: ["receipts", id] as const,
    }),
}
