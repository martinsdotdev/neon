package neon.app.http

import io.circe.Json
import io.circe.parser.parse
import neon.app.auth.*
import neon.common.{HandlingUnitId, LocationId, OrderId, PackagingLevel, Permission, Role, UserId}
import neon.core.{
  AsyncHandlingUnitLifecycleService,
  HandlingUnitEmptyResult,
  HandlingUnitLifecycleError,
  HandlingUnitPackResult,
  HandlingUnitReadyToShipResult,
  HandlingUnitShipResult
}
import neon.handlingunit.{HandlingUnit, HandlingUnitEvent}
import neon.user.User
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class HandlingUnitRoutesSuite extends AnyFunSpec with ScalatestRouteTest:

  private val handlingUnitId = HandlingUnitId()
  private val locationId = LocationId()
  private val orderId = OrderId()
  private val userId = UserId()
  private val at = Instant.now()

  private val hasher = PasswordHasher()
  private val testUser = User(
    id = userId,
    login = "operator",
    name = "Test Operator",
    role = Role.Admin,
    passwordHash = Some(hasher.hash("password")),
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

  private val sessionToken: String = Await
    .result(
      authService.login("operator", "password", None, None),
      5.seconds
    )
    .toOption
    .get
    ._1

  private def stubService(
      packResult: Either[
        HandlingUnitLifecycleError,
        HandlingUnitPackResult
      ]
  ): AsyncHandlingUnitLifecycleService =
    new AsyncHandlingUnitLifecycleService(null):
      override def pack(
          id: HandlingUnitId,
          at: Instant
      ): Future[
        Either[
          HandlingUnitLifecycleError,
          HandlingUnitPackResult
        ]
      ] =
        Future.successful(packResult)

      override def readyToShip(
          id: HandlingUnitId,
          at: Instant
      ): Future[
        Either[
          HandlingUnitLifecycleError,
          HandlingUnitReadyToShipResult
        ]
      ] =
        Future.successful(
          Left(
            HandlingUnitLifecycleError
              .HandlingUnitNotFound(id)
          )
        )

      override def ship(
          id: HandlingUnitId,
          at: Instant
      ): Future[
        Either[
          HandlingUnitLifecycleError,
          HandlingUnitShipResult
        ]
      ] =
        Future.successful(
          Left(
            HandlingUnitLifecycleError
              .HandlingUnitNotFound(id)
          )
        )

      override def empty(
          id: HandlingUnitId,
          at: Instant
      ): Future[
        Either[
          HandlingUnitLifecycleError,
          HandlingUnitEmptyResult
        ]
      ] =
        Future.successful(
          Left(
            HandlingUnitLifecycleError
              .HandlingUnitNotFound(id)
          )
        )

  describe("HandlingUnitRoutes"):
    describe("POST /handling-units/:id/pack"):
      it("returns 200 with packed response on success"):
        val packed = HandlingUnit.Packed(
          handlingUnitId,
          PackagingLevel.Each,
          locationId,
          orderId
        )
        val event = HandlingUnitEvent.HandlingUnitPacked(
          handlingUnitId,
          orderId,
          at
        )
        val result = HandlingUnitPackResult(packed, event)
        val routes = HandlingUnitRoutes(
          stubService(Right(result)),
          authService
        )

        val request = Post(
          s"/handling-units/${handlingUnitId.value}/pack"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("packed")
          )
        }

      it("returns 404 when handling unit not found"):
        val routes = HandlingUnitRoutes(
          stubService(
            Left(
              HandlingUnitLifecycleError
                .HandlingUnitNotFound(handlingUnitId)
            )
          ),
          authService
        )

        val request = Post(
          s"/handling-units/${handlingUnitId.value}/pack"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when handling unit is in wrong state"):
        val routes = HandlingUnitRoutes(
          stubService(
            Left(
              HandlingUnitLifecycleError
                .HandlingUnitInWrongState(handlingUnitId)
            )
          ),
          authService
        )

        val request = Post(
          s"/handling-units/${handlingUnitId.value}/pack"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }

      it("returns 401 without session cookie"):
        val routes = HandlingUnitRoutes(
          stubService(Right(null)),
          authService
        )

        Post(
          s"/handling-units/${handlingUnitId.value}/pack"
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }
