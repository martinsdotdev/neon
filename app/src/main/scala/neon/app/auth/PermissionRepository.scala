package neon.app.auth

import neon.common.{Permission, Role, UserId}

import scala.concurrent.Future

trait PermissionRepository:
  def findRolePermissions(role: Role): Future[Set[Permission]]
  def findUserOverrides(
      userId: UserId
  ): Future[Map[Permission, Effect]]
