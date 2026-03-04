package neon.user

import neon.common.UserId

/** A warehouse operator or system user.
  *
  * @param id
  *   the unique user identifier
  * @param login
  *   the login username
  * @param name
  *   the display name
  * @param active
  *   whether the user is currently active
  */
case class User(
    id: UserId,
    login: String,
    name: String,
    active: Boolean
)
