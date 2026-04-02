import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import {
  getWorkstation,
  getWorkstations,
} from "../api/workstation.api"

export function useWorkstations() {
  return useQuery({
    queryKey: queryKeys.workstations.list(),
    queryFn: getWorkstations,
  })
}

export function useWorkstation(id: string) {
  return useQuery({
    queryKey: queryKeys.workstations.detail(id),
    queryFn: () => getWorkstation(id),
  })
}
