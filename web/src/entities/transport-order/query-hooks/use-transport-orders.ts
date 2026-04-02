import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import {
  getTransportOrder,
  getTransportOrders,
} from "../api/transport-order.api"

export function useTransportOrders() {
  return useQuery({
    queryKey: queryKeys.transportOrders.list(),
    queryFn: getTransportOrders,
  })
}

export function useTransportOrder(id: string) {
  return useQuery({
    queryKey: queryKeys.transportOrders.detail(id),
    queryFn: () => getTransportOrder(id),
  })
}
