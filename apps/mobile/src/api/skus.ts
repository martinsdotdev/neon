import { unwrapForQuery } from "@neon/client/query"
import type { Sku } from "@neon/domain/sku"
import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export const skuQueries = {
  byId: (skuId: string) =>
    queryOptions({
      queryFn: (): Promise<Sku> =>
        unwrapForQuery(apiClient.get<Sku>(`/skus/${skuId}`)),
      queryKey: ["skus", skuId] as const,
      // SKU records change rarely; the projection won't drift mid-session.
      staleTime: 10 * 60_000,
    }),
}
