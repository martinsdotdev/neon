package neon.app.auth

import com.typesafe.scalalogging.LazyLogging
import neon.common.Permission
import net.logstash.logback.argument.StructuredArguments.kv
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{
  AuthorizationFailedRejection,
  Directive1
}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.slf4j.MDC

import scala.concurrent.ExecutionContext

object AuthDirectives extends LazyLogging:

  private val bearerPrefix = "Bearer "

  /** Validates the session and provides an AuthContext. Tries the
    * `Authorization: Bearer <token>` header first (used by mobile and other
    * non-browser clients), then falls back to the `session` cookie (used by
    * the web app). Sets MDC "userId" for downstream logging; cleanup is
    * handled by RequestLoggingDirective.withRequestLogging which restores the
    * previous MDC state after each request.
    */
  def authenticated(authService: AuthenticationService)(using
      ExecutionContext
  ): Directive1[AuthContext] =
    bearerToken.flatMap {
      case Some(token) => validateToken(authService, token)
      case None =>
        optionalCookie("session").flatMap {
          case Some(cookie) => validateToken(authService, cookie.value)
          case None         => complete(StatusCodes.Unauthorized)
        }
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
        // Reject instead of completing so Pekko HTTP's concat can fall
        // through to sibling routes. The route-level RejectionHandler in
        // ProblemRouteHandlers converts unmapped AuthorizationFailedRejection
        // into the RFC 9457 problem-details 403 response.
        reject(AuthorizationFailedRejection)
    }

  private def bearerToken: Directive1[Option[String]] =
    optionalHeaderValueByName("Authorization").map {
      case Some(value) if value.startsWith(bearerPrefix) =>
        Some(value.substring(bearerPrefix.length))
      case _ => None
    }

  private def validateToken(
      authService: AuthenticationService,
      token: String
  )(using ExecutionContext): Directive1[AuthContext] =
    onSuccess(authService.validateSession(token)).flatMap {
      case Right(context) =>
        MDC.put("userId", context.userId.value.toString)
        provide(context)
      case Left(AuthError.AccountInactive) =>
        complete(StatusCodes.Forbidden)
      case Left(_) =>
        complete(StatusCodes.Unauthorized)
    }
