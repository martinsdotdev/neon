package neon.app.http

import neon.app.auth.{
  AuthenticationService,
  InMemoryAsyncUserRepository,
  InMemoryPermissionRepository,
  InMemorySessionRepository,
  PasswordHasher
}
import neon.common.{Permission, Role, UserId}
import neon.user.User
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest

import scala.concurrent.Await
import scala.concurrent.duration.*

/** Authentication scaffolding shared by route suites: an Admin user holding every permission, a
  * wired AuthenticationService, and a logged-in session token for request cookies. Self-typed to
  * [[ScalatestRouteTest]] so the route-test dispatcher is in implicit scope.
  */
trait RouteSuiteBase:
  this: ScalatestRouteTest =>

  protected val authUserId: UserId = UserId()
  protected val hasher: PasswordHasher = PasswordHasher()

  protected val testUser: User = User(
    id = authUserId,
    login = "operator",
    name = "Test Operator",
    role = Role.Admin,
    passwordHash = Some(hasher.hash("password")),
    active = true
  )

  protected val authService: AuthenticationService = AuthenticationService(
    InMemoryAsyncUserRepository(testUser),
    InMemorySessionRepository(),
    InMemoryPermissionRepository(
      Map(Role.Admin -> Permission.values.toSet)
    ),
    hasher
  )

  protected lazy val sessionToken: String = Await
    .result(
      authService
        .login(login = "operator", password = "password", ipAddress = None, userAgent = None),
      5.seconds
    )
    .toOption
    .get
    ._1
