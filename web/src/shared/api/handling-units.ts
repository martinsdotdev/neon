import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface HandlingUnit {
  createdAt: string
  currentLocation: string | null
  id: string
  orderId: string | null
  packagingLevel: "Pallet" | "Case" | "InnerPack" | "Each"
  state: "PickCreated" | "InBuffer" | "Empty" | "ShipCreated" | "Packed" | "ReadyToShip" | "Shipped"
}

const MOCK_HUS: HandlingUnit[] = import.meta.env.DEV
  ? [
      { createdAt: "2026-04-14T08:12:00Z", currentLocation: "l001", id: "hu001", orderId: null, packagingLevel: "Case", state: "PickCreated" },
      { createdAt: "2026-04-14T07:45:00Z", currentLocation: "l004", id: "hu002", orderId: null, packagingLevel: "Each", state: "InBuffer" },
      { createdAt: "2026-04-14T07:55:00Z", currentLocation: null, id: "hu003", orderId: "o004", packagingLevel: "Case", state: "Packed" },
      { createdAt: "2026-04-14T06:00:00Z", currentLocation: null, id: "hu004", orderId: "o002", packagingLevel: "Each", state: "Shipped" },
    ]
  : []

export const handlingUnitQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<HandlingUnit[]>("/api/handling-units")
        return result.unwrapOr(MOCK_HUS)
      },
      queryKey: ["handling-units"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<HandlingUnit>(`/api/handling-units/${id}`)
        return result.unwrapOr(MOCK_HUS.find((hu) => hu.id === id) ?? null)
      },
      queryKey: ["handling-units", id] as const,
    }),
}
