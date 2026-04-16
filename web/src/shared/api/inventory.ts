import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface InventoryRecord {
  available: number
  id: string
  locationId: string
  lot: string | null
  onHand: number
  packagingLevel: "Pallet" | "Case" | "InnerPack" | "Each"
  reserved: number
  skuId: string
  status: "Available" | "QualityHold" | "Damaged" | "Blocked" | "Expired"
}

const MOCK_INV: Array<InventoryRecord> = import.meta.env.DEV
  ? [
      {
        available: 50,
        id: "inv001",
        locationId: "l001",
        lot: null,
        onHand: 60,
        packagingLevel: "Each",
        reserved: 10,
        skuId: "s001",
        status: "Available",
      },
      {
        available: 0,
        id: "inv002",
        locationId: "l002",
        lot: "LOT-2026-B",
        onHand: 25,
        packagingLevel: "Case",
        reserved: 25,
        skuId: "s002",
        status: "Available",
      },
      {
        available: 0,
        id: "inv003",
        locationId: "l003",
        lot: null,
        onHand: 10,
        packagingLevel: "Pallet",
        reserved: 0,
        skuId: "s003",
        status: "QualityHold",
      },
    ]
  : []

export const inventoryQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Array<InventoryRecord>>("/api/inventory")
        return result.unwrapOr(MOCK_INV)
      },
      queryKey: ["inventory"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<InventoryRecord>(
          `/api/inventory/${id}`
        )
        return result.unwrapOr(MOCK_INV.find((inv) => inv.id === id) ?? null)
      },
      queryKey: ["inventory", id] as const,
    }),
}
