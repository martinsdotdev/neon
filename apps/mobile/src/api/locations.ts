import type { Location } from "@neon/domain/location"
import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export const locationQueries = {
  byId: (locationId: string) =>
    queryOptions({
      queryFn: async (): Promise<Location> => {
        const result = await apiClient.get<Location>(`/locations/${locationId}`)
        if (result.isErr()) throw result.error
        return result.value
      },
      queryKey: ["locations", locationId] as const,
      // Location reference data is also stable enough for a long staleTime.
      staleTime: 10 * 60_000,
    }),
}
