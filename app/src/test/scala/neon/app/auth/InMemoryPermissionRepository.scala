package neon.app.auth

import neon.common.{Permission, Role, UserId}

import scala.concurrent.Future

class InMemoryPermissionRepository(
    rolePermissions: Map[Role, Set[Permission]] = Map.empty,
    userOverrides: Map[UserId, Map[Permission, Effect]] = Map.empty
) extends PermissionRepository:

  def findRolePermissions(
      role: Role
  ): Future[Set[Permission]] =
    Future.successful(
      rolePermissions.getOrElse(role, Set.empty)
    )

  def findUserOverrides(
      userId: UserId
  ): Future[Map[Permission, Effect]] =
    Future.successful(
      userOverrides.getOrElse(userId, Map.empty)
    )
