import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import { getSku, getSkus } from "../api/sku.api"

export function useSkus() {
  return useQuery({
    queryKey: queryKeys.skus.list(),
    queryFn: getSkus,
  })
}

export function useSku(id: string) {
  return useQuery({
    queryKey: queryKeys.skus.detail(id),
    queryFn: () => getSku(id),
  })
}
