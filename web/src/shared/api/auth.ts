import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "@tanstack/react-router"
import { apiClient } from "./client"

export interface AuthUser {
  userId: string
  login: string
  name: string
  role: "Admin" | "Supervisor" | "Operator" | "Viewer"
  permissions: string[]
}

const DEV_USER: AuthUser | null =
  import.meta.env.DEV
    ? {
        login: "admin",
        name: "Admin User",
        permissions: [
          "wave:plan", "wave:cancel",
          "task:complete", "task:allocate", "task:assign", "task:cancel",
          "consolidation-group:complete", "consolidation-group:cancel",
          "workstation:assign", "workstation:manage",
          "handling-unit:manage", "slot:manage",
          "inventory:manage", "stock:manage",
          "inbound:manage", "cycle-count:manage",
          "user:manage",
          "transport-order:confirm", "transport-order:cancel",
        ],
        role: "Admin",
        userId: "00000000-0000-0000-0000-000000000001",
      }
    : null

export const authQueries = {
  me: () =>
    queryOptions({
      queryFn: async (): Promise<AuthUser | null> => {
        const result = await apiClient.get<AuthUser>("/api/auth/me")
        return result.match(
          (user) => user,
          () => null,
        )
      },
      queryKey: ["auth", "me"] as const,
      retry: false,
      staleTime: 5 * 60_000,
    }),
}

export const useLogin = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (data: { login: string; password: string }) => {
      const result = await apiClient.post<AuthUser>("/api/auth/login", data)
      if (result.isOk()) return result.value
      if (DEV_USER) return DEV_USER
      throw result.error
    },
    onSuccess: (user) => {
      queryClient.setQueryData(authQueries.me().queryKey, user)
    },
  })
}

export const useLogout = () => {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: async () => {
      const result = await apiClient.post("/api/auth/logout")
      if (result.isErr() && !DEV_USER) throw result.error
    },
    onSuccess: () => {
      queryClient.setQueryData(authQueries.me().queryKey, null)
      navigate({ to: "/login" })
    },
  })
}
