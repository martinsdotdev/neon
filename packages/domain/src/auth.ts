import { z } from "zod"

export type Role = "Admin" | "Supervisor" | "Operator" | "Viewer"

// Mirrors `common/src/main/scala/neon/common/Permission.scala`. Keys must stay
// in lockstep with the Scala enum's `key` values; PermissionContractSuite in
// the backend common module compares this list against the enum and fails the
// build when either side drifts.
export const PERMISSION_KEYS = [
  "wave:plan",
  "wave:cancel",
  "task:complete",
  "task:allocate",
  "task:assign",
  "task:cancel",
  "transport-order:confirm",
  "transport-order:cancel",
  "consolidation-group:complete",
  "consolidation-group:cancel",
  "consolidation-group:advance",
  "workstation:assign",
  "workstation:manage",
  "handling-unit:manage",
  "slot:manage",
  "inventory:manage",
  "stock:manage",
  "inbound:manage",
  "cycle-count:manage",
  "user:manage",
] as const

export type Permission = (typeof PERMISSION_KEYS)[number]

export const PermissionSchema = z.enum(PERMISSION_KEYS)

export interface AuthUser {
  userId: string
  login: string
  name: string
  role: Role
  permissions: Array<string>
}

// Login response. `token` is set for non-browser clients (mobile sends it as
// Authorization: Bearer); browser clients receive it via HttpOnly cookie and
// the field is `undefined`.
export interface AuthLoginResponse extends AuthUser {
  token?: string
}

export const RoleSchema = z.enum(["Admin", "Supervisor", "Operator", "Viewer"])

// `permissions` deliberately parses as plain strings rather than the
// Permission enum: the backend may ship a new key before this mirror updates,
// and an unknown permission must not fail the login response at runtime.
export const AuthUserSchema: z.ZodType<AuthUser> = z.object({
  login: z.string(),
  name: z.string(),
  permissions: z.array(z.string()),
  role: RoleSchema,
  userId: z.string().uuid(),
})

export const AuthLoginResponseSchema: z.ZodType<AuthLoginResponse> = z.object({
  login: z.string(),
  name: z.string(),
  permissions: z.array(z.string()),
  role: RoleSchema,
  token: z.string().optional(),
  userId: z.string().uuid(),
})

export const AuthLoginRequestSchema = z.object({
  login: z.string().min(1),
  password: z.string().min(1),
})

export type AuthLoginRequest = z.infer<typeof AuthLoginRequestSchema>
