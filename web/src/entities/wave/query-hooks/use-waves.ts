import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import { getWave, getWaves } from "../api/wave.api"

export function useWaves() {
  return useQuery({
    queryKey: queryKeys.waves.list(),
    queryFn: getWaves,
  })
}

export function useWave(id: string) {
  return useQuery({
    queryKey: queryKeys.waves.detail(id),
    queryFn: () => getWave(id),
  })
}
