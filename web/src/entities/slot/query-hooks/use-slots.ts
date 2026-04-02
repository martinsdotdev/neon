import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import { getSlot, getSlots } from "../api/slot.api"

export function useSlots() {
  return useQuery({
    queryKey: queryKeys.slots.list(),
    queryFn: getSlots,
  })
}

export function useSlot(id: string) {
  return useQuery({
    queryKey: queryKeys.slots.detail(id),
    queryFn: () => getSlot(id),
  })
}
