import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface MobileLocation {
  id: string
  code: string
  locationType: string
  zoneId: string | null
  pickingSequence: number | null
}

export const locationQueries = {
  byId: (locationId: string) =>
    queryOptions({
      queryFn: async (): Promise<MobileLocation> => {
        const result = await apiClient.get<MobileLocation>(
          `/locations/${locationId}`,
        )
        if (result.isErr()) throw result.error
        return result.value
      },
      queryKey: ["locations", locationId] as const,
      // Location reference data is also stable enough for a long staleTime.
      staleTime: 10 * 60_000,
    }),
}
