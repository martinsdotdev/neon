package neon.user

import neon.common.UserId

/** Port trait for User reference data queries (read-only). */
trait UserRepository:
  def findById(id: UserId): Option[User]
