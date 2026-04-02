import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import {
  getInventory,
  getInventoryItem,
} from "../api/inventory.api"

export function useInventory() {
  return useQuery({
    queryKey: queryKeys.inventory.list(),
    queryFn: getInventory,
  })
}

export function useInventoryItem(id: string) {
  return useQuery({
    queryKey: queryKeys.inventory.detail(id),
    queryFn: () => getInventoryItem(id),
  })
}
