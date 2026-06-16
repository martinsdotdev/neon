import { unwrapForQuery } from "@neon/client/query"
import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"
import type { Location } from "@neon/domain/location"

const MOCK_LOCATIONS: Array<Location> = import.meta.env.DEV
  ? [
      {
        code: "A-01-01",
        id: "l001",
        locationType: "Pick",
        pickingSequence: 1,
        zoneId: "z001",
      },
      {
        code: "A-01-02",
        id: "l002",
        locationType: "Pick",
        pickingSequence: 2,
        zoneId: "z001",
      },
      {
        code: "A-02-01",
        id: "l003",
        locationType: "Reserve",
        pickingSequence: null,
        zoneId: "z001",
      },
      {
        code: "B-01-01",
        id: "l004",
        locationType: "Buffer",
        pickingSequence: null,
        zoneId: "z002",
      },
      {
        code: "S-01",
        id: "l005",
        locationType: "Staging",
        pickingSequence: null,
        zoneId: null,
      },
      {
        code: "D-01",
        id: "l006",
        locationType: "Dock",
        pickingSequence: null,
        zoneId: null,
      },
      {
        code: "P-01",
        id: "l007",
        locationType: "Packing",
        pickingSequence: null,
        zoneId: null,
      },
    ]
  : []

export const locationQueries = {
  all: () =>
    queryOptions({
      queryFn: async () =>
        unwrapForQuery(apiClient.get<Array<Location>>("/api/locations"), {
          fallback: import.meta.env.DEV ? MOCK_LOCATIONS : undefined,
        }),
      queryKey: ["locations"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () =>
        unwrapForQuery(apiClient.get<Location>(`/api/locations/${id}`), {
          fallback: import.meta.env.DEV
            ? (MOCK_LOCATIONS.find((l) => l.id === id) ?? null)
            : undefined,
        }),
      queryKey: ["locations", id] as const,
    }),
}
