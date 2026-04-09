package neon.app.http

import neon.app.auth.*
import neon.common.{
  HandlingUnitId,
  LocationId,
  PackagingLevel,
  Permission,
  Role,
  TransportOrderId,
  UserId
}
import neon.core.{
  AsyncTransportOrderCancellationService,
  AsyncTransportOrderConfirmationService,
  TransportOrderCancellationError,
  TransportOrderCancellationResult,
  TransportOrderConfirmationError,
  TransportOrderConfirmationResult
}
import neon.handlingunit.{HandlingUnit, HandlingUnitEvent}
import neon.transportorder.{TransportOrder, TransportOrderEvent}
import neon.user.User
import io.circe.Json
import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class TransportOrderRoutesSuite
    extends AnyFunSpec
    with ScalatestRouteTest:

  private val transportOrderId = TransportOrderId()
  private val handlingUnitId = HandlingUnitId()
  private val userId = UserId()
  private val sourceLocation = LocationId()
  private val destinationLocation = LocationId()
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

  private def stubConfirmationService(
      result: Either[
        TransportOrderConfirmationError,
        TransportOrderConfirmationResult
      ]
  ): AsyncTransportOrderConfirmationService =
    new AsyncTransportOrderConfirmationService(
      null,
      null,
      null,
      null
    ):
      override def confirm(
          transportOrderId: TransportOrderId,
          at: Instant
      ): Future[
        Either[
          TransportOrderConfirmationError,
          TransportOrderConfirmationResult
        ]
      ] =
        Future.successful(result)

  private def stubCancellationService(
      result: Either[
        TransportOrderCancellationError,
        TransportOrderCancellationResult
      ]
  ): AsyncTransportOrderCancellationService =
    new AsyncTransportOrderCancellationService(null):
      override def cancel(
          id: TransportOrderId,
          at: Instant
      ): Future[
        Either[
          TransportOrderCancellationError,
          TransportOrderCancellationResult
        ]
      ] =
        Future.successful(result)

  describe("TransportOrderRoutes"):
    describe("POST /transport-orders/:id/confirm"):
      it("returns 200 with confirmation response on success"):
        val confirmed = TransportOrder.Confirmed(
          transportOrderId,
          handlingUnitId,
          destinationLocation
        )
        val confirmedEvent =
          TransportOrderEvent.TransportOrderConfirmed(
            transportOrderId,
            handlingUnitId,
            destinationLocation,
            at
          )
        val inBuffer = HandlingUnit.InBuffer(
          handlingUnitId,
          PackagingLevel.Each,
          destinationLocation
        )
        val handlingUnitEvent =
          HandlingUnitEvent.HandlingUnitMovedToBuffer(
            handlingUnitId,
            destinationLocation,
            at
          )
        val result = TransportOrderConfirmationResult(
          confirmed = confirmed,
          confirmedEvent = confirmedEvent,
          handlingUnit = inBuffer,
          handlingUnitEvent = handlingUnitEvent,
          bufferCompletion = None
        )
        val routes = TransportOrderRoutes(
          stubConfirmationService(Right(result)),
          stubCancellationService(Right(null)),
          authService
        )

        val request = Post(
          s"/transport-orders/${transportOrderId.value}/confirm"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("confirmed")
          )
        }

      it("returns 404 when transport order not found"):
        val routes = TransportOrderRoutes(
          stubConfirmationService(
            Left(
              TransportOrderConfirmationError
                .TransportOrderNotFound(transportOrderId)
            )
          ),
          stubCancellationService(Right(null)),
          authService
        )

        val request = Post(
          s"/transport-orders/${transportOrderId.value}/confirm"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when transport order is not pending"):
        val routes = TransportOrderRoutes(
          stubConfirmationService(
            Left(
              TransportOrderConfirmationError
                .TransportOrderNotPending(transportOrderId)
            )
          ),
          stubCancellationService(Right(null)),
          authService
        )

        val request = Post(
          s"/transport-orders/${transportOrderId.value}/confirm"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }

    describe("DELETE /transport-orders/:id"):
      it("returns 200 with cancellation response on success"):
        val cancelled = TransportOrder.Cancelled(
          transportOrderId,
          handlingUnitId,
          destinationLocation
        )
        val cancelledEvent =
          TransportOrderEvent.TransportOrderCancelled(
            transportOrderId,
            handlingUnitId,
            destinationLocation,
            at
          )
        val result = TransportOrderCancellationResult(
          cancelled = cancelled,
          event = cancelledEvent
        )
        val routes = TransportOrderRoutes(
          stubConfirmationService(Right(null)),
          stubCancellationService(Right(result)),
          authService
        )

        val request = Delete(
          s"/transport-orders/${transportOrderId.value}"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("cancelled")
          )
        }

      it("returns 404 when transport order not found"):
        val routes = TransportOrderRoutes(
          stubConfirmationService(Right(null)),
          stubCancellationService(
            Left(
              TransportOrderCancellationError
                .TransportOrderNotFound(transportOrderId)
            )
          ),
          authService
        )

        val request = Delete(
          s"/transport-orders/${transportOrderId.value}"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 401 without session cookie"):
        val routes = TransportOrderRoutes(
          stubConfirmationService(Right(null)),
          stubCancellationService(Right(null)),
          authService
        )

        Post(
          s"/transport-orders/${transportOrderId.value}/confirm"
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }
