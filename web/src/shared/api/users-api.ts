import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface User {
  active: boolean
  id: string
  login: string
  name: string
  role: "Admin" | "Supervisor" | "Operator" | "Viewer"
}

const MOCK_USERS: User[] = import.meta.env.DEV
  ? [
      { active: true, id: "u001", login: "admin", name: "Admin User", role: "Admin" },
      { active: true, id: "u002", login: "jsmith", name: "John Smith", role: "Supervisor" },
      { active: true, id: "u003", login: "mjones", name: "Maria Jones", role: "Operator" },
      { active: true, id: "u004", login: "awilson", name: "Alice Wilson", role: "Operator" },
      { active: false, id: "u005", login: "blee", name: "Bob Lee", role: "Viewer" },
    ]
  : []

export const userQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<User[]>("/api/users")
        return result.unwrapOr(MOCK_USERS)
      },
      queryKey: ["users"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<User>(`/api/users/${id}`)
        return result.unwrapOr(MOCK_USERS.find((u) => u.id === id) ?? null)
      },
      queryKey: ["users", id] as const,
    }),
}
