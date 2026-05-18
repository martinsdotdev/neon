import { createApiClient } from "@neon/client/client"

export type { ApiError, ProblemDetails } from "@neon/domain/error"

// Web uses HttpOnly session cookies — no token getter, so the factory sends
// `credentials: "include"`.
export const apiClient = createApiClient({ baseUrl: "" })
