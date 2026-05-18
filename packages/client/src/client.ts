import type { ApiError, ProblemDetails } from "@neon/domain/error"
import { ResultAsync, err } from "neverthrow"

// A function that returns the auth token for the next request. May be sync
// (web returns undefined to fall back to the HttpOnly cookie) or async
// (mobile reads from expo-secure-store).
export type AuthTokenGetter = () =>
  | string
  | undefined
  | null
  | Promise<string | undefined | null>

export interface ApiClientConfig {
  baseUrl: string
  getAuthToken?: AuthTokenGetter
}

export interface ApiClient {
  get: <T>(path: string, init?: RequestExtras) => ResultAsync<T, ApiError>
  post: <T>(
    path: string,
    body?: unknown,
    init?: RequestExtras,
  ) => ResultAsync<T, ApiError>
  del: <T>(path: string, init?: RequestExtras) => ResultAsync<T, ApiError>
}

export interface RequestExtras {
  headers?: Record<string, string>
  // Allow per-request idempotency keys, scoped overrides, etc.
}

const FALLBACK_PROBLEM_TYPE = "about:blank"

export const createApiClient = (config: ApiClientConfig): ApiClient => {
  const request = <T>(
    method: string,
    path: string,
    body?: unknown,
    extras?: RequestExtras,
  ): ResultAsync<T, ApiError> =>
    ResultAsync.fromPromise(
      resolveHeaders(config, body, extras).then((headers) =>
        fetch(joinUrl(config.baseUrl, path), {
          body: body === undefined ? undefined : JSON.stringify(body),
          credentials: shouldSendCookies(config) ? "include" : "same-origin",
          headers,
          method,
        }),
      ),
      (error): ApiError => ({ kind: "network", message: String(error) }),
    ).andThen((res) => parseResponse<T>(res))

  return {
    del: <T>(path: string, init?: RequestExtras) =>
      request<T>("DELETE", path, undefined, init),
    get: <T>(path: string, init?: RequestExtras) =>
      request<T>("GET", path, undefined, init),
    post: <T>(path: string, body?: unknown, init?: RequestExtras) =>
      request<T>("POST", path, body, init),
  }
}

const joinUrl = (baseUrl: string, path: string): string => {
  if (!baseUrl) return path
  if (baseUrl.endsWith("/") && path.startsWith("/")) {
    return baseUrl + path.slice(1)
  }
  if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
    return `${baseUrl}/${path}`
  }
  return baseUrl + path
}

// Web omits getAuthToken, so we send cookies. Mobile passes a Bearer-token
// getter, so cookies are unnecessary (the backend session is matched via the
// Authorization header). Keeping cookies "same-origin" on mobile avoids
// accidentally leaking unrelated cookies from a webview.
const shouldSendCookies = (config: ApiClientConfig): boolean =>
  config.getAuthToken === undefined

const resolveHeaders = async (
  config: ApiClientConfig,
  body: unknown,
  extras: RequestExtras | undefined,
): Promise<Record<string, string>> => {
  const headers: Record<string, string> = { ...(extras?.headers ?? {}) }
  if (body !== undefined && headers["Content-Type"] === undefined) {
    headers["Content-Type"] = "application/json"
  }
  if (config.getAuthToken !== undefined) {
    const token = await config.getAuthToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }
  return headers
}

const parseResponse = <T>(res: Response): ResultAsync<T, ApiError> => {
  if (!res.ok) return parseProblem(res)
  if (res.status === 204) return okAsync(undefined as T)
  return ResultAsync.fromPromise(
    res.json() as Promise<T>,
    (): ApiError => ({ kind: "network", message: "Failed to parse response" }),
  )
}

const parseProblem = <T>(res: Response): ResultAsync<T, ApiError> => {
  const fallback: ProblemDetails = {
    status: res.status,
    title: res.statusText || "Request failed",
    type: FALLBACK_PROBLEM_TYPE,
  }
  // Try to parse a body regardless of Content-Type — backends in transition to
  // RFC 9457 may emit JSON error bodies with `application/json` instead of
  // `application/problem+json`. Fall back to the fabricated problem if the
  // body isn't JSON or isn't shaped like ProblemDetails.
  return ResultAsync.fromPromise(
    res.json().catch(() => fallback) as Promise<ProblemDetails>,
    (): ApiError => ({ kind: "problem", problem: fallback }),
  ).andThen((problem) =>
    err<T, ApiError>({ kind: "problem" as const, problem }),
  )
}

const okAsync = <T>(value: T): ResultAsync<T, ApiError> =>
  ResultAsync.fromSafePromise(Promise.resolve(value))
