package neon.app.repository

import neon.common.{Role, UserId}
import neon.user.{AsyncUserRepository, User}
import io.r2dbc.spi.{ConnectionFactory, Row}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** R2DBC-backed implementation of [[AsyncUserRepository]]. */
class R2dbcUserRepository(connectionFactory: ConnectionFactory)(using
    system: org.apache.pekko.actor.typed.ActorSystem[?],
    ec: ExecutionContext
) extends AsyncUserRepository:

  def findById(id: UserId): Future[Option[User]] =
    R2dbcHelper.queryOne(
      connectionFactory,
      "SELECT id, login, name, role, password_hash, active FROM users WHERE id = $1",
      id.value
    )(mapRow)

  def findByLogin(login: String): Future[Option[User]] =
    R2dbcHelper.queryOne(
      connectionFactory,
      "SELECT id, login, name, role, password_hash, active FROM users WHERE login = $1",
      login
    )(mapRow)

  private def mapRow(row: Row): User =
    User(
      id = UserId(row.get("id", classOf[UUID])),
      login = row.get("login", classOf[String]),
      name = row.get("name", classOf[String]),
      role = Role.valueOf(row.get("role", classOf[String])),
      passwordHash = Option(row.get("password_hash", classOf[String])),
      active = row.get("active", classOf[java.lang.Boolean]).booleanValue
    )
