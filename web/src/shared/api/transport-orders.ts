import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface TransportOrder {
  createdAt: string
  destination: string
  handlingUnitId: string
  id: string
  state: "Pending" | "Confirmed" | "Cancelled"
}

const MOCK_TOS: TransportOrder[] = import.meta.env.DEV
  ? [
      { createdAt: "2026-04-14T08:15:00Z", destination: "l004", handlingUnitId: "hu001", id: "to001", state: "Pending" },
      { createdAt: "2026-04-14T07:50:00Z", destination: "l005", handlingUnitId: "hu002", id: "to002", state: "Confirmed" },
      { createdAt: "2026-04-13T14:10:00Z", destination: "l004", handlingUnitId: "hu003", id: "to003", state: "Cancelled" },
    ]
  : []

export const transportOrderQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<TransportOrder[]>("/api/transport-orders")
        return result.unwrapOr(MOCK_TOS)
      },
      queryKey: ["transport-orders"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<TransportOrder>(`/api/transport-orders/${id}`)
        return result.unwrapOr(MOCK_TOS.find((to) => to.id === id) ?? null)
      },
      queryKey: ["transport-orders", id] as const,
    }),
}
