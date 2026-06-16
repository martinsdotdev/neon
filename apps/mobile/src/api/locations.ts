import { unwrapForQuery } from "@neon/client/query"
import type { Location } from "@neon/domain/location"
import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export const locationQueries = {
  byId: (locationId: string) =>
    queryOptions({
      queryFn: (): Promise<Location> =>
        unwrapForQuery(apiClient.get<Location>(`/locations/${locationId}`)),
      queryKey: ["locations", locationId] as const,
      // Location reference data is also stable enough for a long staleTime.
      staleTime: 10 * 60_000,
    }),
}
