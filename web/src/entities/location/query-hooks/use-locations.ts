import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import {
  getLocation,
  getLocations,
} from "../api/location.api"

export function useLocations() {
  return useQuery({
    queryKey: queryKeys.locations.list(),
    queryFn: getLocations,
  })
}

export function useLocation(id: string) {
  return useQuery({
    queryKey: queryKeys.locations.detail(id),
    queryFn: () => getLocation(id),
  })
}
