import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface MobileSku {
  id: string
  code: string
  description: string
  lotManaged: boolean
}

export const skuQueries = {
  byId: (skuId: string) =>
    queryOptions({
      queryFn: async (): Promise<MobileSku> => {
        const result = await apiClient.get<MobileSku>(`/skus/${skuId}`)
        if (result.isErr()) throw result.error
        return result.value
      },
      queryKey: ["skus", skuId] as const,
      // SKU records change rarely; the projection won't drift mid-session.
      staleTime: 10 * 60_000,
    }),
}
