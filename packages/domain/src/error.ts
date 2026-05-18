import { z } from "zod"

// RFC 9457 Problem Details for HTTP APIs. Backend emits this for all error
// responses with `Content-Type: application/problem+json`. See ADR 0011.
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

export const ProblemDetailsSchema: z.ZodType<ProblemDetails> = z.object({
  detail: z.string().optional(),
  instance: z.string().optional(),
  status: z.number().int(),
  title: z.string(),
  type: z.string(),
})
