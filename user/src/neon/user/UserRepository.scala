package neon.user

import neon.common.UserId

/** Port trait for [[User]] reference data queries (read-only). */
trait UserRepository:

  /** Finds a user by their unique identifier.
    *
    * @param id
    *   the user identifier
    * @return
    *   the user if they exist, [[None]] otherwise
    */
  def findById(id: UserId): Option[User]
