package neon.app.auth

import io.r2dbc.spi.ConnectionFactory
import neon.app.repository.R2dbcHelper
import neon.common.{Permission, Role, UserId}
import org.apache.pekko.actor.typed.ActorSystem

import scala.concurrent.{ExecutionContext, Future}

class R2dbcPermissionRepository(
    connectionFactory: ConnectionFactory
)(using ActorSystem[?], ExecutionContext)
    extends PermissionRepository:

  def findRolePermissions(
      role: Role
  ): Future[Set[Permission]] =
    R2dbcHelper
      .queryList(
        connectionFactory,
        "SELECT permission FROM role_permissions WHERE role = $1",
        role.toString
      )(row => row.get("permission", classOf[String]))
      .map { keys =>
        keys.flatMap(Permission.fromKey).toSet
      }

  def findUserOverrides(
      userId: UserId
  ): Future[Map[Permission, Effect]] =
    R2dbcHelper
      .queryList(
        connectionFactory,
        """SELECT permission, effect
          |FROM user_permission_overrides
          |WHERE user_id = $1""".stripMargin,
        userId.value
      ) { row =>
        val key = row.get("permission", classOf[String])
        val effect = row.get("effect", classOf[String])
        (key, effect)
      }
      .map { rows =>
        rows.flatMap { (key, effect) =>
          for
            permission <- Permission.fromKey(key)
            eff <- effect match
              case "allow" => Some(Effect.Allow)
              case "deny"  => Some(Effect.Deny)
              case _       => None
          yield permission -> eff
        }.toMap
      }
