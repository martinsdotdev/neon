package neon.app.auth

import neon.common.Permission
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives.*

import scala.concurrent.ExecutionContext

object AuthDirectives:

  def authenticated(authService: AuthenticationService)(using
      ExecutionContext
  ): Directive1[AuthContext] =
    optionalCookie("session").flatMap {
      case Some(cookie) =>
        onSuccess(authService.validateSession(cookie.value))
          .flatMap {
            case Right(context)                  => provide(context)
            case Left(AuthError.AccountInactive) =>
              complete(StatusCodes.Forbidden)
            case Left(_) =>
              complete(StatusCodes.Unauthorized)
          }
      case None =>
        complete(StatusCodes.Unauthorized)
    }

  def requirePermission(
      permission: Permission,
      authService: AuthenticationService
  )(using ExecutionContext): Directive1[AuthContext] =
    authenticated(authService).flatMap { context =>
      if context.hasPermission(permission) then provide(context)
      else complete(StatusCodes.Forbidden)
    }
