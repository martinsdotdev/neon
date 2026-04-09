package neon.app.http

import neon.app.auth.*
import neon.common.{HandlingUnitId, OrderId, Permission, Role, SlotId, UserId, WorkstationId}
import neon.core.{
  AsyncSlotService,
  SlotCompleteResult,
  SlotError,
  SlotReleaseResult,
  SlotReserveResult
}
import neon.slot.{Slot, SlotEvent}
import neon.user.User
import io.circe.Json
import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class SlotRoutesSuite extends AnyFunSpec with ScalatestRouteTest:

  private val slotId = SlotId()
  private val workstationId = WorkstationId()
  private val orderId = OrderId()
  private val handlingUnitId = HandlingUnitId()
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
      reserveResult: Either[SlotError, SlotReserveResult],
      completeResult: Either[SlotError, SlotCompleteResult] = Left(
        SlotError.SlotNotFound(SlotId())
      ),
      releaseResult: Either[SlotError, SlotReleaseResult] = Left(SlotError.SlotNotFound(SlotId()))
  ): AsyncSlotService =
    new AsyncSlotService(null):
      override def reserve(
          id: SlotId,
          orderId: OrderId,
          handlingUnitId: HandlingUnitId,
          at: Instant
      ): Future[Either[SlotError, SlotReserveResult]] =
        Future.successful(reserveResult)

      override def complete(
          id: SlotId,
          at: Instant
      ): Future[Either[SlotError, SlotCompleteResult]] =
        Future.successful(completeResult)

      override def release(
          id: SlotId,
          at: Instant
      ): Future[Either[SlotError, SlotReleaseResult]] =
        Future.successful(releaseResult)

  describe("SlotRoutes"):
    describe("POST /slots/:id/reserve"):
      it("returns 200 with reserved response on success"):
        val reserved = Slot.Reserved(
          slotId,
          workstationId,
          orderId,
          handlingUnitId
        )
        val event = SlotEvent.SlotReserved(
          slotId,
          workstationId,
          orderId,
          handlingUnitId,
          at
        )
        val result = SlotReserveResult(reserved, event)
        val routes = SlotRoutes(
          stubService(Right(result)),
          authService
        )
        val body = s"""{
          |"orderId": "${orderId.value}",
          |"handlingUnitId": "${handlingUnitId.value}"
          |}""".stripMargin

        val request = Post(
          s"/slots/${slotId.value}/reserve",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("reserved")
          )
        }

      it("returns 404 when slot not found"):
        val routes = SlotRoutes(
          stubService(
            Left(SlotError.SlotNotFound(slotId))
          ),
          authService
        )
        val body = s"""{
          |"orderId": "${orderId.value}",
          |"handlingUnitId": "${handlingUnitId.value}"
          |}""".stripMargin

        val request = Post(
          s"/slots/${slotId.value}/reserve",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when slot is in wrong state"):
        val routes = SlotRoutes(
          stubService(
            Left(SlotError.SlotInWrongState(slotId))
          ),
          authService
        )
        val body = s"""{
          |"orderId": "${orderId.value}",
          |"handlingUnitId": "${handlingUnitId.value}"
          |}""".stripMargin

        val request = Post(
          s"/slots/${slotId.value}/reserve",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }

      it("returns 401 without session cookie"):
        val routes = SlotRoutes(
          stubService(Right(null)),
          authService
        )
        val body = s"""{
          |"orderId": "${orderId.value}",
          |"handlingUnitId": "${handlingUnitId.value}"
          |}""".stripMargin

        Post(
          s"/slots/${slotId.value}/reserve",
          HttpEntity(ContentTypes.`application/json`, body)
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }
