package neon.app.http

import neon.app.auth.*
import neon.common.{OrderId, Permission, Role, UserId, WaveId}
import neon.core.{
  AsyncWaveCancellationService,
  AsyncWavePlanningService,
  DockCarrierAssignment,
  WaveCancellationError,
  WaveCancellationResult,
  WavePlanningError,
  WavePlanningResult
}
import neon.order.{AsyncOrderRepository, Order}
import neon.user.User
import neon.wave.{OrderGrouping, WaveEvent}
import io.circe.Json
import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class WaveRoutesSuite extends AnyFunSpec with ScalatestRouteTest:

  private val waveId = WaveId()
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

  private val stubOrderRepo = new AsyncOrderRepository:
    def findById(id: OrderId) = Future.successful(None)
    def findByIds(ids: List[OrderId]) =
      Future.successful(Nil)

  private def stubCancellationService(
      result: Either[
        WaveCancellationError,
        WaveCancellationResult
      ]
  ): AsyncWaveCancellationService =
    new AsyncWaveCancellationService(
      null,
      null,
      null,
      null
    ):
      override def cancel(
          waveId: WaveId,
          at: Instant
      ): Future[
        Either[
          WaveCancellationError,
          WaveCancellationResult
        ]
      ] =
        Future.successful(result)

  private def stubPlanningService(
      result: Either[WavePlanningError, WavePlanningResult]
  ): AsyncWavePlanningService =
    new AsyncWavePlanningService(
      null, null, null, null, null
    ):
      override def planAndRelease(
          orders: List[Order],
          grouping: OrderGrouping,
          dockAssignments: List[DockCarrierAssignment],
          at: Instant,
          lineResolution: (
              WaveId,
              OrderId,
              neon.order.OrderLine
          ) => List[neon.wave.TaskRequest]
      ): Future[
        Either[WavePlanningError, WavePlanningResult]
      ] =
        Future.successful(result)

  describe("WaveRoutes"):
    describe("DELETE /waves/:id"):
      it("returns 200 with cancellation response on success"):
        val cancelledWave = neon.wave.Wave.Cancelled(
          waveId,
          OrderGrouping.Single
        )
        val cancelledEvent = WaveEvent.WaveCancelled(
          waveId,
          OrderGrouping.Single,
          at
        )
        val result = WaveCancellationResult(
          cancelled = cancelledWave,
          cancelledEvent = cancelledEvent,
          cancelledTasks = Nil,
          cancelledTransportOrders = Nil,
          cancelledConsolidationGroups = Nil
        )
        val routes = WaveRoutes(
          stubCancellationService(Right(result)),
          stubPlanningService(Right(null)),
          stubOrderRepo,
          authService
        )

        val request = Delete(
          s"/waves/${waveId.value}"
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

      it("returns 404 when wave not found"):
        val routes = WaveRoutes(
          stubCancellationService(
            Left(
              WaveCancellationError.WaveNotFound(waveId)
            )
          ),
          stubPlanningService(Right(null)),
          stubOrderRepo,
          authService
        )

        val request = Delete(
          s"/waves/${waveId.value}"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when wave is already terminal"):
        val routes = WaveRoutes(
          stubCancellationService(
            Left(
              WaveCancellationError
                .WaveAlreadyTerminal(waveId)
            )
          ),
          stubPlanningService(Right(null)),
          stubOrderRepo,
          authService
        )

        val request = Delete(
          s"/waves/${waveId.value}"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }

      it("returns 401 without session cookie"):
        val routes = WaveRoutes(
          stubCancellationService(Right(null)),
          stubPlanningService(Right(null)),
          stubOrderRepo,
          authService
        )

        Delete(
          s"/waves/${waveId.value}"
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }
