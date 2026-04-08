package neon.app.http

import neon.app.auth.{AuthContext, AuthDirectives, AuthError, AuthenticationService}
import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.HttpCookie
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext

import CirceSupport.given

object AuthRoutes:

  private case class LoginRequest(
      login: String,
      password: String
  ) derives Decoder

  private case class AuthResponse(
      userId: String,
      login: String,
      name: String,
      role: String,
      permissions: List[String]
  ) derives Encoder.AsObject

  private object AuthResponse:
    def fromContext(context: AuthContext): AuthResponse =
      AuthResponse(
        userId = context.userId.value.toString,
        login = context.login,
        name = context.name,
        role = context.role.toString,
        permissions = context.permissions.map(_.key).toList.sorted
      )

  private val cookieMaxAge = 30L * 24 * 60 * 60

  private def sessionCookie(
      token: String,
      secure: Boolean
  ): HttpCookie =
    HttpCookie(
      name = "session",
      value = token,
      httpOnly = true,
      secure = secure,
      path = Some("/"),
      maxAge = Some(cookieMaxAge),
      extension = Some("SameSite=Lax")
    )

  private def clearCookie(secure: Boolean): HttpCookie =
    HttpCookie(
      name = "session",
      value = "",
      httpOnly = true,
      secure = secure,
      path = Some("/"),
      maxAge = Some(0),
      extension = Some("SameSite=Lax")
    )

  def apply(
      authService: AuthenticationService,
      secureCookies: Boolean = true
  )(using ExecutionContext): Route =
    pathPrefix("auth"):
      concat(
        path("login"):
          post:
            entity(as[LoginRequest]): request =>
              extractClientIP: clientIp =>
                optionalHeaderValueByName("User-Agent"): userAgent =>
                  onSuccess(
                    authService.login(
                      request.login,
                      request.password,
                      Some(clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")),
                      userAgent
                    )
                  ):
                    case Right((token, context)) =>
                      setCookie(sessionCookie(token, secureCookies)):
                        complete(
                          AuthResponse.fromContext(context)
                        )
                    case Left(
                          AuthError.InvalidCredentials
                        ) =>
                      complete(StatusCodes.Unauthorized)
                    case Left(AuthError.AccountInactive) =>
                      complete(StatusCodes.Forbidden)
                    case Left(_) =>
                      complete(StatusCodes.Unauthorized)
        ,
        path("logout"):
          post:
            optionalCookie("session"):
              case Some(cookie) =>
                onSuccess(
                  authService.logout(cookie.value)
                ):
                  setCookie(clearCookie(secureCookies)):
                    complete(StatusCodes.OK)
              case None =>
                complete(StatusCodes.OK)
        ,
        path("me"):
          get:
            AuthDirectives
              .authenticated(authService): context =>
                complete(
                  AuthResponse.fromContext(context)
                )
      )
