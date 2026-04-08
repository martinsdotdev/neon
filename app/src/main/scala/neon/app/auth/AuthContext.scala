package neon.app.auth

import neon.common.{Permission, Role, UserId}

case class AuthContext(
    userId: UserId,
    login: String,
    name: String,
    role: Role,
    permissions: Set[Permission]
):
  def hasPermission(permission: Permission): Boolean =
    permissions.contains(permission)
