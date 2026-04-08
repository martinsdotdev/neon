package neon.user

import neon.common.UserId

import scala.concurrent.Future

/** Async port trait for [[User]] reference data queries. */
trait AsyncUserRepository:
  def findById(id: UserId): Future[Option[User]]
  def findByLogin(login: String): Future[Option[User]]
