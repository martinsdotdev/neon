package neon.app.auth

import neon.app.repository.R2dbcHelper
import neon.common.UserId
import io.r2dbc.spi.{ConnectionFactory, Row}
import org.apache.pekko.actor.typed.ActorSystem

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class R2dbcSessionRepository(connectionFactory: ConnectionFactory)(using
    ActorSystem[?],
    ExecutionContext
) extends SessionRepository:

  def create(session: Session): Future[Unit] =
    R2dbcHelper
      .execute(
        connectionFactory,
        """INSERT INTO sessions
          |(id, token_hash, user_id, expires_at, created_at,
          |ip_address, user_agent)
          |VALUES ($1, $2, $3, $4, $5, $6, $7)""".stripMargin,
        session.id,
        session.tokenHash,
        session.userId.value,
        OffsetDateTime.ofInstant(session.expiresAt, ZoneOffset.UTC),
        OffsetDateTime.ofInstant(session.createdAt, ZoneOffset.UTC),
        session.ipAddress.orNull,
        session.userAgent.orNull
      )
      .map(_ => ())

  def findByTokenHash(
      tokenHash: String
  ): Future[Option[Session]] =
    R2dbcHelper.queryOne(
      connectionFactory,
      """SELECT id, token_hash, user_id, expires_at, created_at,
        |ip_address, user_agent
        |FROM sessions WHERE token_hash = $1""".stripMargin,
      tokenHash
    )(mapRow)

  def delete(tokenHash: String): Future[Unit] =
    R2dbcHelper
      .execute(
        connectionFactory,
        "DELETE FROM sessions WHERE token_hash = $1",
        tokenHash
      )
      .map(_ => ())

  def deleteAllForUser(userId: UserId): Future[Unit] =
    R2dbcHelper
      .execute(
        connectionFactory,
        "DELETE FROM sessions WHERE user_id = $1",
        userId.value
      )
      .map(_ => ())

  def extendExpiry(
      tokenHash: String,
      newExpiry: Instant
  ): Future[Unit] =
    R2dbcHelper
      .execute(
        connectionFactory,
        "UPDATE sessions SET expires_at = $1 WHERE token_hash = $2",
        OffsetDateTime.ofInstant(newExpiry, ZoneOffset.UTC),
        tokenHash
      )
      .map(_ => ())

  private def mapRow(row: Row): Session =
    Session(
      id = row.get("id", classOf[UUID]),
      tokenHash = row.get("token_hash", classOf[String]),
      userId = UserId(row.get("user_id", classOf[UUID])),
      expiresAt = row
        .get("expires_at", classOf[OffsetDateTime])
        .toInstant,
      createdAt = row
        .get("created_at", classOf[OffsetDateTime])
        .toInstant,
      ipAddress = Option(row.get("ip_address", classOf[String])),
      userAgent = Option(row.get("user_agent", classOf[String]))
    )
