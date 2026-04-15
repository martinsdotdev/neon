import type { AuthUser } from "@/shared/api/auth"

export const useHasPermission = (
  user: AuthUser | null | undefined,
  permission: string,
): boolean => {
  if (!user) {return false}
  return user.permissions.includes(permission)
}
