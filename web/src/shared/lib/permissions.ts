import type { AuthUser } from "@/shared/api/auth"

export const useHasPermission = (
  user: AuthUser | null | undefined,
  permission: string,
): boolean => {
  if (!user) {return false}
  return user.permissions.includes(permission)
}

export interface PermissionDomain {
  key: string
  label: string
  permissions: string[]
}

export const PERMISSION_DOMAINS: PermissionDomain[] = [
  {
    key: "outbound",
    label: "Outbound",
    permissions: [
      "wave:plan",
      "wave:cancel",
      "task:complete",
      "task:allocate",
      "task:assign",
      "task:cancel",
    ],
  },
  {
    key: "consolidation",
    label: "Consolidation",
    permissions: [
      "consolidation-group:complete",
      "consolidation-group:cancel",
    ],
  },
  {
    key: "transport",
    label: "Transport",
    permissions: [
      "transport-order:confirm",
      "transport-order:cancel",
    ],
  },
  {
    key: "workstation",
    label: "Workstation",
    permissions: [
      "workstation:assign",
      "workstation:manage",
    ],
  },
  {
    key: "handling",
    label: "Handling & Slots",
    permissions: ["handling-unit:manage", "slot:manage"],
  },
  {
    key: "inventory",
    label: "Inventory",
    permissions: [
      "inventory:manage",
      "stock:manage",
      "cycle-count:manage",
    ],
  },
  {
    key: "other",
    label: "Inbound & Admin",
    permissions: ["inbound:manage", "user:manage"],
  },
]

export const formatPermissionLabel = (permission: string): string =>
  permission
    .replace(/[-:]/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase())
