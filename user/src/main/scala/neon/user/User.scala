package neon.user

import neon.common.{Role, UserId}

/** A warehouse operator or system user.
  *
  * @param id
  *   the unique user identifier
  * @param login
  *   the login username
  * @param name
  *   the display name
  * @param role
  *   the authorization role
  * @param passwordHash
  *   the Argon2id password hash, absent for users without credentials
  * @param active
  *   whether the user is currently active
  */
case class User(
    id: UserId,
    login: String,
    name: String,
    role: Role,
    passwordHash: Option[String],
    active: Boolean
)
