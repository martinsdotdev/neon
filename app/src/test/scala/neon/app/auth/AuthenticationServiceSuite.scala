package neon.app.auth

import neon.common.{Permission, Role, UserId}
import neon.user.User
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Duration
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

class AuthenticationServiceSuite extends AnyFunSpec with EitherValues:

  private given ExecutionContext =
    ExecutionContext.global

  private val hasher = PasswordHasher()
  private val passwordHash = hasher.hash("correct-password")

  private val activeUser = User(
    id = UserId(),
    login = "operator",
    name = "Test Operator",
    role = Role.Operator,
    passwordHash = Some(passwordHash),
    active = true
  )

  private val inactiveUser = activeUser.copy(
    id = UserId(),
    login = "inactive",
    active = false
  )

  private def service(
      users: Seq[User] = Seq(activeUser, inactiveUser),
      rolePermissions: Map[Role, Set[Permission]] = Map(
        Role.Operator -> Set(
          Permission.TaskComplete,
          Permission.TransportOrderConfirm
        )
      ),
      userOverrides: Map[UserId, Map[Permission, Effect]] = Map.empty
  ): AuthenticationService =
    AuthenticationService(
      InMemoryAsyncUserRepository(users*),
      InMemorySessionRepository(),
      InMemoryPermissionRepository(rolePermissions, userOverrides),
      hasher
    )

  private def await[T](f: Future[T]): T =
    Await.result(f, 5.seconds)

  describe("AuthenticationService"):
    describe("login"):
      it("succeeds with valid credentials and returns a session token"):
        val svc = service()
        val result = await(
          svc.login("operator", "correct-password", None, None)
        )
        val (token, context) = result.value
        assert(token.nonEmpty)
        assert(context.login == "operator")
        assert(context.role == Role.Operator)

      it("fails with unknown login"):
        val svc = service()
        val result = await(
          svc.login("nobody", "correct-password", None, None)
        )
        assert(result.left.value == AuthError.InvalidCredentials)

      it("fails with wrong password"):
        val svc = service()
        val result = await(
          svc.login("operator", "wrong-password", None, None)
        )
        assert(result.left.value == AuthError.InvalidCredentials)

      it("fails for inactive user"):
        val svc = service()
        val result = await(
          svc.login("inactive", "correct-password", None, None)
        )
        assert(result.left.value == AuthError.AccountInactive)

      it("fails for user without password hash"):
        val noPasswordUser = activeUser.copy(
          id = UserId(),
          login = "nopw",
          passwordHash = None
        )
        val svc = service(users = Seq(noPasswordUser))
        val result = await(
          svc.login("nopw", "any-password", None, None)
        )
        assert(
          result.left.value == AuthError.InvalidCredentials
        )

      it("includes effective permissions in the returned context"):
        val svc = service()
        val (_, context) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        assert(
          context.permissions == Set(
            Permission.TaskComplete,
            Permission.TransportOrderConfirm
          )
        )

    describe("validateSession"):
      it("validates a token returned from login"):
        val svc = service()
        val (token, _) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        val context = await(svc.validateSession(token)).value
        assert(context.login == "operator")

      it("rejects an unknown token"):
        val svc = service()
        val result = await(svc.validateSession("bogus-token"))
        assert(result.left.value == AuthError.SessionNotFound)

      it("rejects an expired session"):
        val svc = AuthenticationService(
          InMemoryAsyncUserRepository(activeUser),
          InMemorySessionRepository(),
          InMemoryPermissionRepository(
            Map(Role.Operator -> Set(Permission.TaskComplete))
          ),
          hasher,
          sessionMaxAge = Duration.ofMillis(1),
          sessionRenewalThreshold = Duration.ZERO
        )
        val (token, _) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        Thread.sleep(5)
        val result = await(svc.validateSession(token))
        assert(result.left.value == AuthError.SessionExpired)

      it("rejects a session for a deactivated user"):
        val userRepo =
          InMemoryAsyncUserRepository(activeUser, inactiveUser)
        val svc = AuthenticationService(
          userRepo,
          InMemorySessionRepository(),
          InMemoryPermissionRepository(
            Map(Role.Operator -> Set(Permission.TaskComplete))
          ),
          hasher
        )
        val (token, _) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        userRepo.updateUser(activeUser.copy(active = false))
        val result = await(svc.validateSession(token))
        assert(
          result.left.value == AuthError.AccountInactive
        )

    describe("logout"):
      it("invalidates the session token"):
        val svc = service()
        val (token, _) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        await(svc.logout(token))
        val result = await(svc.validateSession(token))
        assert(result.left.value == AuthError.SessionNotFound)

    describe("logoutAll"):
      it("invalidates all sessions for a user"):
        val svc = service()
        val (token1, _) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        val (token2, _) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        await(svc.logoutAll(activeUser.id))
        assert(
          await(svc.validateSession(token1)).left.value
            == AuthError.SessionNotFound
        )
        assert(
          await(svc.validateSession(token2)).left.value
            == AuthError.SessionNotFound
        )

    describe("effective permissions"):
      it("returns role default permissions"):
        val svc = service()
        val (_, context) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        assert(
          context.permissions == Set(
            Permission.TaskComplete,
            Permission.TransportOrderConfirm
          )
        )

      it("adds permissions granted by user override"):
        val svc = service(
          userOverrides = Map(
            activeUser.id -> Map(
              Permission.WavePlan -> Effect.Allow
            )
          )
        )
        val (_, context) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        assert(context.permissions.contains(Permission.WavePlan))
        assert(
          context.permissions.contains(Permission.TaskComplete)
        )

      it("removes permissions denied by user override"):
        val svc = service(
          userOverrides = Map(
            activeUser.id -> Map(
              Permission.TaskComplete -> Effect.Deny
            )
          )
        )
        val (_, context) = await(
          svc.login("operator", "correct-password", None, None)
        ).value
        assert(
          !context.permissions.contains(Permission.TaskComplete)
        )
        assert(
          context.permissions
            .contains(Permission.TransportOrderConfirm)
        )
