import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import {
  getConsolidationGroup,
  getConsolidationGroups,
} from "../api/consolidation-group.api"

export function useConsolidationGroups() {
  return useQuery({
    queryKey: queryKeys.consolidationGroups.list(),
    queryFn: getConsolidationGroups,
  })
}

export function useConsolidationGroup(id: string) {
  return useQuery({
    queryKey: queryKeys.consolidationGroups.detail(id),
    queryFn: () => getConsolidationGroup(id),
  })
}
