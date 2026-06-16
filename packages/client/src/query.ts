import type { ApiError } from "@neon/domain/error"
import type { ResultAsync } from "neverthrow"

/** Error thrown by [[unwrapForQuery]] when a request fails and no fallback was
 * provided. Carries the structured [[ApiError]] so React Query error
 * boundaries and toasts can inspect `kind` / `problem` rather than a bare
 * message string. */
export class ApiRequestError extends Error {
  readonly apiError: ApiError

  constructor(apiError: ApiError) {
    super(
      apiError.kind === "problem" ? apiError.problem.title : apiError.message
    )
    this.name = "ApiRequestError"
    this.apiError = apiError
  }
}

export interface UnwrapOptions<T> {
  /** Value to return instead of throwing when the request fails. Web passes
   * its development mock here (`import.meta.env.DEV ? MOCK : undefined`) so
   * production surfaces the error to React Query while local development keeps
   * rendering offline. Mobile omits it, so every failure throws. */
  fallback?: T
}

/** The single sanctioned way to consume a client `ResultAsync` inside a
 * TanStack Query `queryFn` / `mutationFn`. On success it returns the value; on
 * failure it returns `options.fallback` when one is set, otherwise it throws
 * [[ApiRequestError]] so React Query enters its error state and retries.
 *
 * A fallback counts as set only when it is not `undefined`, so the web pattern
 * `fallback: import.meta.env.DEV ? MOCK : undefined` throws in production
 * (where the ternary is `undefined`) and serves the mock in development. */
export const unwrapForQuery = async <T>(
  result: ResultAsync<T, ApiError>,
  options: UnwrapOptions<T> = {}
): Promise<T> => {
  const settled = await result
  if (settled.isOk()) {
    return settled.value
  }
  if (options.fallback !== undefined) {
    return options.fallback
  }
  throw new ApiRequestError(settled.error)
}
