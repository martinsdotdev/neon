package neon.user

import neon.common.UserId

case class User(
    id: UserId,
    login: String,
    name: String,
    active: Boolean
)
