package neon.user

import neon.common.UserId

import scala.concurrent.Future

/** Async port trait for [[User]] reference data queries (read-only). */
trait AsyncUserRepository:
  def findById(id: UserId): Future[Option[User]]
