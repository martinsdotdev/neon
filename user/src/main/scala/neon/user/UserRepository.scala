package neon.user

import neon.common.UserId

/** Port trait for [[User]] reference data queries. */
trait UserRepository:

  /** Finds a user by their unique identifier. */
  def findById(id: UserId): Option[User]

  /** Finds a user by their login username. */
  def findByLogin(login: String): Option[User]
