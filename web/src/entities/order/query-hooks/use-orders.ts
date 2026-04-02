import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import { getOrder, getOrders } from "../api/order.api"

export function useOrders() {
  return useQuery({
    queryKey: queryKeys.orders.list(),
    queryFn: getOrders,
  })
}

export function useOrder(id: string) {
  return useQuery({
    queryKey: queryKeys.orders.detail(id),
    queryFn: () => getOrder(id),
  })
}
