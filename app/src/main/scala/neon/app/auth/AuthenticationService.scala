package neon.app.auth

import com.github.f4b6a3.uuid.UuidCreator
import com.typesafe.scalalogging.LazyLogging
import neon.common.{Permission, Role, UserId}
import neon.user.{AsyncUserRepository, User}
import net.logstash.logback.argument.StructuredArguments.kv

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}

class AuthenticationService(
    userRepository: AsyncUserRepository,
    sessionRepository: SessionRepository,
    permissionRepository: PermissionRepository,
    passwordHasher: PasswordHasher,
    sessionMaxAge: Duration = Duration.ofDays(30),
    sessionRenewalThreshold: Duration = Duration.ofDays(15)
)(using ExecutionContext)
    extends LazyLogging:

  def login(
      login: String,
      password: String,
      ipAddress: Option[String],
      userAgent: Option[String]
  ): Future[Either[AuthError, (String, AuthContext)]] =
    userRepository.findByLogin(login).flatMap {
      case None =>
        logger.warn("Login failed: unknown user")
        Future.successful(Left(AuthError.InvalidCredentials))
      case Some(user) if !user.active =>
        logger.warn(
          "Login failed: account inactive {}",
          kv("userId", user.id.value)
        )
        Future.successful(Left(AuthError.AccountInactive))
      case Some(user) =>
        user.passwordHash match
          case None =>
            logger.warn(
              "Login failed: no password hash {}",
              kv("userId", user.id.value)
            )
            Future.successful(
              Left(AuthError.InvalidCredentials)
            )
          case Some(hash) if !passwordHasher.verify(password, hash) =>
            logger.warn(
              "Login failed: invalid credentials {}",
              kv("userId", user.id.value)
            )
            Future.successful(
              Left(AuthError.InvalidCredentials)
            )
          case Some(_) =>
            val token = SessionToken.generate()
            val now = Instant.now()
            val session = Session(
              id = UuidCreator.getTimeOrderedEpoch(),
              tokenHash = SessionToken.hash(token),
              userId = user.id,
              expiresAt = now.plus(sessionMaxAge),
              createdAt = now,
              ipAddress = ipAddress,
              userAgent = userAgent
            )
            for
              _ <- sessionRepository.create(session)
              context <- buildAuthContext(user)
            yield
              logger.info(
                "Login successful {} {} {}",
                kv("login", login),
                kv("userId", user.id.value),
                kv("ipAddress", ipAddress.getOrElse("unknown"))
              )
              Right(token, context)
    }

  def validateSession(
      token: String
  ): Future[Either[AuthError, AuthContext]] =
    val tokenHash = SessionToken.hash(token)
    val now = Instant.now()
    sessionRepository.findByTokenHash(tokenHash).flatMap {
      case None =>
        logger.debug("Session not found")
        Future.successful(Left(AuthError.SessionNotFound))
      case Some(session) if session.expiresAt.isBefore(now) =>
        logger.debug(
          "Session expired {}",
          kv("userId", session.userId.value)
        )
        sessionRepository
          .delete(tokenHash)
          .map(_ => Left(AuthError.SessionExpired))
      case Some(session) =>
        val renewalPoint =
          session.expiresAt.minus(sessionRenewalThreshold)
        val renewal =
          if now.isAfter(renewalPoint) then
            logger.debug(
              "Renewing session {}",
              kv("userId", session.userId.value)
            )
            sessionRepository.extendExpiry(
              tokenHash,
              now.plus(sessionMaxAge)
            )
          else Future.successful(())

        renewal.flatMap { _ =>
          userRepository.findById(session.userId).flatMap {
            case None =>
              sessionRepository
                .delete(tokenHash)
                .map(_ => Left(AuthError.SessionNotFound))
            case Some(user) if !user.active =>
              logger.warn(
                "Session invalidated: account inactive {}",
                kv("userId", user.id.value)
              )
              sessionRepository
                .delete(tokenHash)
                .map(_ => Left(AuthError.AccountInactive))
            case Some(user) =>
              buildAuthContext(user).map(Right(_))
          }
        }
    }

  def logout(token: String): Future[Unit] =
    sessionRepository.delete(SessionToken.hash(token))

  def logoutAll(userId: UserId): Future[Unit] =
    sessionRepository.deleteAllForUser(userId)

  private def buildAuthContext(
      user: User
  ): Future[AuthContext] =
    computeEffectivePermissions(user.role, user.id)
      .map { permissions =>
        AuthContext(
          userId = user.id,
          login = user.login,
          name = user.name,
          role = user.role,
          permissions = permissions
        )
      }

  private def computeEffectivePermissions(
      role: Role,
      userId: UserId
  ): Future[Set[Permission]] =
    val rolePermissionsFuture =
      permissionRepository.findRolePermissions(role)
    val userOverridesFuture =
      permissionRepository.findUserOverrides(userId)

    rolePermissionsFuture
      .zip(userOverridesFuture)
      .map { (rolePermissions, userOverrides) =>
        userOverrides.foldLeft(rolePermissions) {
          case (acc, (permission, Effect.Deny))  => acc - permission
          case (acc, (permission, Effect.Allow)) => acc + permission
        }
      }
