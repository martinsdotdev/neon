package neon.app.repository

import neon.common.UserId
import neon.user.{AsyncUserRepository, User}
import io.r2dbc.spi.{ConnectionFactory, Row}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** R2DBC-backed implementation of [[AsyncUserRepository]]. */
class R2dbcUserRepository(connectionFactory: ConnectionFactory)(using
    ExecutionContext
) extends AsyncUserRepository:

  def findById(id: UserId): Future[Option[User]] =
    R2dbcHelper
      .queryOne(
        connectionFactory,
        "SELECT id, login, name, active FROM users WHERE id = $1",
        id.value
      )(mapRow)

  private def mapRow(row: Row): User =
    User(
      id = UserId(row.get("id", classOf[UUID])),
      login = row.get("login", classOf[String]),
      name = row.get("name", classOf[String]),
      active = row.get("active", classOf[java.lang.Boolean]).booleanValue
    )
