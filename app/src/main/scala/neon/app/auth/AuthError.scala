package neon.app.auth

import neon.common.Permission

sealed trait AuthError

object AuthError:
  case object InvalidCredentials extends AuthError
  case object SessionExpired extends AuthError
  case object SessionNotFound extends AuthError
  case object AccountInactive extends AuthError
  case class InsufficientPermissions(required: Permission) extends AuthError
