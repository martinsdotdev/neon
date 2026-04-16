import { ResultAsync, err, ok } from "neverthrow"

export interface ProblemDetails {
  detail?: string
  instance?: string
  status: number
  title: string
  type: string
}

export type ApiError =
  | { kind: "problem"; problem: ProblemDetails }
  | { kind: "network"; message: string }

const request = <T>(
  method: string,
  path: string,
  body?: unknown
): ResultAsync<T, ApiError> =>
  ResultAsync.fromPromise(
    fetch(path, {
      body: body ? JSON.stringify(body) : undefined,
      headers: body ? { "Content-Type": "application/json" } : {},
      method,
    }),
    (error): ApiError => ({ kind: "network", message: String(error) })
  ).andThen((res) => {
    if (!res.ok) {
      return ResultAsync.fromPromise(
        res.json().catch(() => ({
          status: res.status,
          title: res.statusText,
          type: "about:blank",
        })),
        (): ApiError => ({
          kind: "problem",
          problem: {
            status: res.status,
            title: res.statusText,
            type: "about:blank",
          },
        })
      ).andThen((problem: ProblemDetails) =>
        err({ kind: "problem" as const, problem })
      )
    }

    if (res.status === 204) {
      return ok(undefined as T)
    }

    return ResultAsync.fromPromise(
      res.json() as Promise<T>,
      (): ApiError => ({ kind: "network", message: "Failed to parse response" })
    )
  })

export const apiClient = {
  del: <T>(path: string) => request<T>("DELETE", path),
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
}
