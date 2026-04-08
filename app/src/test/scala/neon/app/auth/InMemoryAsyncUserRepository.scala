package neon.app.auth

import neon.common.UserId
import neon.user.{AsyncUserRepository, User}

import scala.concurrent.Future

class InMemoryAsyncUserRepository(users: User*) extends AsyncUserRepository:
  private val userList =
    scala.collection.mutable.ArrayBuffer.from(users)

  def findById(id: UserId): Future[Option[User]] =
    Future.successful(userList.find(_.id == id))

  def findByLogin(login: String): Future[Option[User]] =
    Future.successful(userList.find(_.login == login))

  def updateUser(user: User): Unit =
    val idx = userList.indexWhere(_.id == user.id)
    if idx >= 0 then userList(idx) = user
