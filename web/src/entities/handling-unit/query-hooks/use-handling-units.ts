import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import {
  getHandlingUnit,
  getHandlingUnits,
} from "../api/handling-unit.api"

export function useHandlingUnits() {
  return useQuery({
    queryKey: queryKeys.handlingUnits.list(),
    queryFn: getHandlingUnits,
  })
}

export function useHandlingUnit(id: string) {
  return useQuery({
    queryKey: queryKeys.handlingUnits.detail(id),
    queryFn: () => getHandlingUnit(id),
  })
}
