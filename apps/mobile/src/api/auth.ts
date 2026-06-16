import { unwrapForQuery } from "@neon/client/query"
import type {
  AuthLoginRequest,
  AuthLoginResponse,
  AuthUser,
} from "@neon/domain/auth"
import { apiClient, clearAuthToken, setAuthToken } from "./client"

export const login = async (
  credentials: AuthLoginRequest,
): Promise<AuthLoginResponse> => {
  const response = await unwrapForQuery(
    apiClient.post<AuthLoginResponse>("/auth/login", credentials),
  )
  if (!response.token) {
    throw new Error("Login response missing token (mobile clients require it)")
  }
  await setAuthToken(response.token)
  return response
}

export const fetchCurrentUser = async (): Promise<AuthUser | null> => {
  const result = await apiClient.get<AuthUser>("/auth/me")
  return result.match(
    (user) => user,
    () => null,
  )
}

export const logout = async (): Promise<void> => {
  // Best-effort: the server-side session is invalidated by deleting the
  // stored hash; if the request fails (offline, network), the local token
  // is still cleared so the operator can re-authenticate.
  await apiClient.post("/auth/logout")
  await clearAuthToken()
}
