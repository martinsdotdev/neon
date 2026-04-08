package neon.app.auth

import neon.app.http.AuthRoutes
import neon.common.{Permission, Role, UserId}
import neon.user.User
import io.circe.Json
import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import scala.concurrent.Await
import scala.concurrent.duration.*

class AuthRoutesSuite extends AnyFunSpec with ScalatestRouteTest:

  private val hasher = PasswordHasher()

  private val testUser = User(
    id = UserId(),
    login = "admin",
    name = "Test Admin",
    role = Role.Admin,
    passwordHash = Some(hasher.hash("secret123")),
    active = true
  )

  private val authService = AuthenticationService(
    InMemoryAsyncUserRepository(testUser),
    InMemorySessionRepository(),
    InMemoryPermissionRepository(
      Map(Role.Admin -> Permission.values.toSet)
    ),
    hasher
  )

  private val routes =
    AuthRoutes(authService, secureCookies = false)

  private def loginJson(
      login: String,
      password: String
  ): HttpEntity.Strict =
    HttpEntity(
      ContentTypes.`application/json`,
      s"""{"login":"$login","password":"$password"}"""
    )

  private def validSessionToken(): String =
    Await
      .result(
        authService.login("admin", "secret123", None, None),
        5.seconds
      )
      .toOption
      .get
      ._1

  describe("AuthRoutes"):
    describe("POST /auth/login"):
      it("returns 200 and sets session cookie on success"):
        Post(
          "/auth/login",
          loginJson("admin", "secret123")
        ) ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val setCookie =
            header[`Set-Cookie`].map(_.cookie)
          assert(setCookie.isDefined)
          assert(setCookie.get.name == "session")
          assert(setCookie.get.httpOnly)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("login")
              .contains("admin")
          )
          assert(
            json.hcursor
              .get[String]("role")
              .contains("Admin")
          )
          val permissions = json.hcursor
            .get[List[String]]("permissions")
            .getOrElse(Nil)
          assert(permissions.nonEmpty)
          assert(permissions.contains("wave:plan"))
        }

      it("returns 401 on wrong password"):
        Post(
          "/auth/login",
          loginJson("admin", "wrong")
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }

      it("returns 401 on unknown user"):
        Post(
          "/auth/login",
          loginJson("nobody", "secret123")
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }

    describe("GET /auth/me"):
      it("returns auth context when authenticated"):
        val token = validSessionToken()
        val request =
          Get("/auth/me").addHeader(
            Cookie("session", token)
          )
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("login")
              .contains("admin")
          )
        }

      it("returns 401 without session cookie"):
        Get("/auth/me") ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }

    describe("POST /auth/logout"):
      it("clears session from server"):
        val token = validSessionToken()
        val request =
          Post("/auth/logout").addHeader(
            Cookie("session", token)
          )
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
        }

      it("returns 200 even without session cookie"):
        Post("/auth/logout") ~> routes ~> check {
          assert(status == StatusCodes.OK)
        }
