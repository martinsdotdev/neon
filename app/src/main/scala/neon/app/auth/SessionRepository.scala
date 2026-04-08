package neon.app.auth

import neon.common.UserId

import java.time.Instant
import scala.concurrent.Future

trait SessionRepository:
  def create(session: Session): Future[Unit]
  def findByTokenHash(tokenHash: String): Future[Option[Session]]
  def delete(tokenHash: String): Future[Unit]
  def deleteAllForUser(userId: UserId): Future[Unit]
  def extendExpiry(
      tokenHash: String,
      newExpiry: Instant
  ): Future[Unit]
