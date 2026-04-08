package neon.app.auth

import neon.common.UserId

import java.time.Instant
import scala.concurrent.Future

class InMemorySessionRepository extends SessionRepository:
  private val store =
    scala.collection.mutable.Map.empty[String, Session]

  def create(session: Session): Future[Unit] =
    Future.successful(
      store += (session.tokenHash -> session)
    )

  def findByTokenHash(
      tokenHash: String
  ): Future[Option[Session]] =
    Future.successful(store.get(tokenHash))

  def delete(tokenHash: String): Future[Unit] =
    Future.successful(store -= tokenHash)

  def deleteAllForUser(userId: UserId): Future[Unit] =
    Future.successful(
      store.filterInPlace((_, s) => s.userId != userId)
    )

  def extendExpiry(
      tokenHash: String,
      newExpiry: Instant
  ): Future[Unit] =
    Future.successful(
      store.updateWith(tokenHash)(
        _.map(_.copy(expiresAt = newExpiry))
      )
    )
