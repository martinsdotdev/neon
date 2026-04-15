import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface Location {
  code: string
  id: string
  locationType: "Pick" | "Reserve" | "Buffer" | "Staging" | "Packing" | "Dock"
  pickingSequence: number | null
  zoneId: string | null
}

const MOCK_LOCATIONS: Location[] = import.meta.env.DEV
  ? [
      { code: "A-01-01", id: "l001", locationType: "Pick", pickingSequence: 1, zoneId: "z001" },
      { code: "A-01-02", id: "l002", locationType: "Pick", pickingSequence: 2, zoneId: "z001" },
      { code: "A-02-01", id: "l003", locationType: "Reserve", pickingSequence: null, zoneId: "z001" },
      { code: "B-01-01", id: "l004", locationType: "Buffer", pickingSequence: null, zoneId: "z002" },
      { code: "S-01", id: "l005", locationType: "Staging", pickingSequence: null, zoneId: null },
      { code: "D-01", id: "l006", locationType: "Dock", pickingSequence: null, zoneId: null },
      { code: "P-01", id: "l007", locationType: "Packing", pickingSequence: null, zoneId: null },
    ]
  : []

export const locationQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Location[]>("/api/locations")
        return result.unwrapOr(MOCK_LOCATIONS)
      },
      queryKey: ["locations"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Location>(`/api/locations/${id}`)
        return result.unwrapOr(MOCK_LOCATIONS.find((l) => l.id === id) ?? null)
      },
      queryKey: ["locations", id] as const,
    }),
}
