import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import {
  getCarrier,
  getCarriers,
} from "../api/carrier.api"

export function useCarriers() {
  return useQuery({
    queryKey: queryKeys.carriers.list(),
    queryFn: getCarriers,
  })
}

export function useCarrier(id: string) {
  return useQuery({
    queryKey: queryKeys.carriers.detail(id),
    queryFn: () => getCarrier(id),
  })
}
