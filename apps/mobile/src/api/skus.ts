import type { Sku } from "@neon/domain/sku"
import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export const skuQueries = {
  byId: (skuId: string) =>
    queryOptions({
      queryFn: async (): Promise<Sku> => {
        const result = await apiClient.get<Sku>(`/skus/${skuId}`)
        if (result.isErr()) throw result.error
        return result.value
      },
      queryKey: ["skus", skuId] as const,
      // SKU records change rarely; the projection won't drift mid-session.
      staleTime: 10 * 60_000,
    }),
}
