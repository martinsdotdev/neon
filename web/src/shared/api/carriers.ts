import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface Carrier {
  active: boolean
  code: string
  id: string
  name: string
}

// Dev mock data -- used when backend is unavailable
const MOCK_CARRIERS: Carrier[] = import.meta.env.DEV
  ? [
      { active: true, code: "DHL", id: "c001", name: "DHL Express" },
      { active: true, code: "FDX", id: "c002", name: "FedEx" },
      { active: true, code: "UPS", id: "c003", name: "UPS" },
      { active: false, code: "USPS", id: "c004", name: "USPS" },
      { active: true, code: "DPD", id: "c005", name: "DPD Group" },
      { active: true, code: "GLS", id: "c006", name: "GLS" },
    ]
  : []

export const carrierQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Carrier[]>("/api/carriers")
        return result.unwrapOr(MOCK_CARRIERS)
      },
      queryKey: ["carriers"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Carrier>(`/api/carriers/${id}`)
        return result.unwrapOr(
          MOCK_CARRIERS.find((c) => c.id === id) ?? null,
        )
      },
      queryKey: ["carriers", id] as const,
    }),
}
