import type { AuthLoginRequest, AuthUser } from "@neon/domain/auth"
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react"
import {
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
} from "@/src/api/auth"

interface AuthState {
  user: AuthUser | null
  status: "loading" | "authenticated" | "unauthenticated"
}

interface AuthContextValue extends AuthState {
  login: (credentials: AuthLoginRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [state, setState] = useState<AuthState>({
    status: "loading",
    user: null,
  })

  useEffect(() => {
    let cancelled = false
    fetchCurrentUser().then((user) => {
      if (cancelled) return
      setState({
        status: user ? "authenticated" : "unauthenticated",
        user,
      })
    })
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (credentials: AuthLoginRequest) => {
    const response = await loginRequest(credentials)
    setState({
      status: "authenticated",
      user: {
        login: response.login,
        name: response.name,
        permissions: response.permissions,
        role: response.role,
        userId: response.userId,
      },
    })
  }, [])

  const logout = useCallback(async () => {
    await logoutRequest()
    setState({ status: "unauthenticated", user: null })
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ ...state, login, logout }),
    [state, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = (): AuthContextValue => {
  const value = useContext(AuthContext)
  if (value === null) {
    throw new Error("useAuth must be used inside <AuthProvider>")
  }
  return value
}
