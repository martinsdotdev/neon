import type {
  AuthLoginRequest,
  AuthLoginResponse,
  AuthUser,
} from "@neon/domain/auth"
import { apiClient, clearAuthToken, setAuthToken } from "./client"

export const login = async (
  credentials: AuthLoginRequest,
): Promise<AuthLoginResponse> => {
  const result = await apiClient.post<AuthLoginResponse>(
    "/auth/login",
    credentials,
  )
  if (result.isErr()) throw result.error
  if (!result.value.token) {
    throw new Error("Login response missing token (mobile clients require it)")
  }
  await setAuthToken(result.value.token)
  return result.value
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
