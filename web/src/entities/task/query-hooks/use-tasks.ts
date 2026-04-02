import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import { getTask, getTasks } from "../api/task.api"

export function useTasks() {
  return useQuery({
    queryKey: queryKeys.tasks.list(),
    queryFn: getTasks,
  })
}

export function useTask(id: string) {
  return useQuery({
    queryKey: queryKeys.tasks.detail(id),
    queryFn: () => getTask(id),
  })
}
