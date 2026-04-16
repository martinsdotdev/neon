import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface OrderLine {
  packagingLevel: "Pallet" | "Case" | "InnerPack" | "Each"
  quantity: number
  skuId: string
}

export interface Order {
  carrierId: string | null
  id: string
  lines: OrderLine[]
  priority: "Low" | "Normal" | "High" | "Critical"
}

const MOCK_ORDERS: Order[] = import.meta.env.DEV
  ? [
      { carrierId: "c001", id: "o001", lines: [{ packagingLevel: "Each", quantity: 5, skuId: "s001" }, { packagingLevel: "Case", quantity: 2, skuId: "s003" }], priority: "High" },
      { carrierId: "c002", id: "o002", lines: [{ packagingLevel: "Each", quantity: 10, skuId: "s002" }], priority: "Normal" },
      { carrierId: null, id: "o003", lines: [{ packagingLevel: "Pallet", quantity: 1, skuId: "s005" }], priority: "Low" },
      { carrierId: "c003", id: "o004", lines: [{ packagingLevel: "Each", quantity: 3, skuId: "s001" }, { packagingLevel: "Each", quantity: 7, skuId: "s004" }], priority: "Critical" },
      { carrierId: "c001", id: "o005", lines: [{ packagingLevel: "Each", quantity: 12, skuId: "s002" }], priority: "Normal" },
      { carrierId: "c002", id: "o006", lines: [{ packagingLevel: "Case", quantity: 4, skuId: "s003" }], priority: "Normal" },
      { carrierId: null, id: "o007", lines: [{ packagingLevel: "Each", quantity: 8, skuId: "s001" }], priority: "Low" },
      { carrierId: "c003", id: "o008", lines: [{ packagingLevel: "Each", quantity: 6, skuId: "s004" }, { packagingLevel: "Case", quantity: 3, skuId: "s002" }], priority: "High" },
      { carrierId: "c001", id: "o009", lines: [{ packagingLevel: "Each", quantity: 15, skuId: "s005" }], priority: "Normal" },
      { carrierId: null, id: "o010", lines: [{ packagingLevel: "Pallet", quantity: 2, skuId: "s001" }], priority: "Low" },
    ]
  : []

export const orderQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Order[]>("/api/orders")
        return result.unwrapOr(MOCK_ORDERS)
      },
      queryKey: ["orders"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Order>(`/api/orders/${id}`)
        return result.unwrapOr(MOCK_ORDERS.find((o) => o.id === id) ?? null)
      },
      queryKey: ["orders", id] as const,
    }),
}
