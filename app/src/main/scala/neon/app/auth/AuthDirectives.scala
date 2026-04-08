package neon.app.auth

import com.typesafe.scalalogging.LazyLogging
import neon.common.Permission
import net.logstash.logback.argument.StructuredArguments.kv
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.slf4j.MDC

import scala.concurrent.ExecutionContext

object AuthDirectives extends LazyLogging:

  /** Validates the session cookie and provides an AuthContext. Sets MDC "userId" for downstream
    * logging; cleanup is handled by RequestLoggingDirective.withRequestLogging which restores the
    * previous MDC state after each request.
    */
  def authenticated(authService: AuthenticationService)(using
      ExecutionContext
  ): Directive1[AuthContext] =
    optionalCookie("session").flatMap {
      case Some(cookie) =>
        onSuccess(authService.validateSession(cookie.value))
          .flatMap {
            case Right(context) =>
              MDC.put(
                "userId",
                context.userId.value.toString
              )
              provide(context)
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
      else
        logger.warn(
          "Permission denied {} {}",
          kv("userId", context.userId.value),
          kv("requiredPermission", permission.key)
        )
        complete(StatusCodes.Forbidden)
    }
