import { unwrapForQuery } from "@neon/client/query"
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
      queryFn: async () =>
        unwrapForQuery(
          apiClient.get<Array<Receipt>>("/api/inbound/receipts"),
          {
            fallback: import.meta.env.DEV ? MOCK_RECEIPTS : undefined,
          }
        ),
      queryKey: ["receipts"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () =>
        unwrapForQuery(
          apiClient.get<Receipt>(`/api/inbound/receipts/${id}`),
          {
            fallback: import.meta.env.DEV
              ? (MOCK_RECEIPTS.find((r) => r.id === id) ?? null)
              : undefined,
          }
        ),
      queryKey: ["receipts", id] as const,
    }),
}
