import { useQuery } from "@tanstack/react-query"
import { queryKeys } from "@/shared/api/query-keys"
import { getUser, getUsers } from "../api/user.api"

export function useUsers() {
  return useQuery({
    queryKey: queryKeys.users.list(),
    queryFn: getUsers,
  })
}

export function useUser(id: string) {
  return useQuery({
    queryKey: queryKeys.users.detail(id),
    queryFn: () => getUser(id),
  })
}
