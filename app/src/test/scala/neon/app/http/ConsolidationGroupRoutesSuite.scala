package neon.app.http

import neon.app.auth.*
import neon.common.{
  ConsolidationGroupId,
  Permission,
  Role,
  UserId,
  WaveId,
  WorkstationId,
  WorkstationMode
}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.core.{
  AsyncConsolidationGroupCancellationService,
  AsyncConsolidationGroupCompletionService,
  ConsolidationGroupCancellationError,
  ConsolidationGroupCancellationResult,
  ConsolidationGroupCompletionError,
  ConsolidationGroupCompletionResult
}
import neon.user.User
import neon.workstation.{Workstation, WorkstationEvent, WorkstationType}
import io.circe.Json
import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class ConsolidationGroupRoutesSuite extends AnyFunSpec with ScalatestRouteTest:

  private val consolidationGroupId = ConsolidationGroupId()
  private val workstationId = WorkstationId()
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

  private def stubCompletionService(
      result: Either[
        ConsolidationGroupCompletionError,
        ConsolidationGroupCompletionResult
      ]
  ): AsyncConsolidationGroupCompletionService =
    new AsyncConsolidationGroupCompletionService(
      null,
      null
    ):
      override def complete(
          consolidationGroupId: ConsolidationGroupId,
          at: Instant
      ): Future[
        Either[
          ConsolidationGroupCompletionError,
          ConsolidationGroupCompletionResult
        ]
      ] =
        Future.successful(result)

  private def stubCancellationService(
      result: Either[
        ConsolidationGroupCancellationError,
        ConsolidationGroupCancellationResult
      ]
  ): AsyncConsolidationGroupCancellationService =
    new AsyncConsolidationGroupCancellationService(null):
      override def cancel(
          id: ConsolidationGroupId,
          at: Instant
      ): Future[
        Either[
          ConsolidationGroupCancellationError,
          ConsolidationGroupCancellationResult
        ]
      ] =
        Future.successful(result)

  describe("ConsolidationGroupRoutes"):
    describe("POST /consolidation-groups/:id/complete"):
      it("returns 200 with completion response on success"):
        val completed = ConsolidationGroup.Completed(
          consolidationGroupId,
          waveId,
          Nil,
          workstationId
        )
        val completedEvent =
          ConsolidationGroupEvent.ConsolidationGroupCompleted(
            consolidationGroupId,
            waveId,
            workstationId,
            at
          )
        val idle = Workstation.Idle(
          workstationId,
          WorkstationType.PutWall,
          8,
          WorkstationMode.Picking
        )
        val workstationEvent =
          WorkstationEvent.WorkstationReleased(
            workstationId,
            WorkstationType.PutWall,
            at
          )
        val result = ConsolidationGroupCompletionResult(
          completed = completed,
          completedEvent = completedEvent,
          workstation = idle,
          workstationEvent = workstationEvent
        )
        val routes = ConsolidationGroupRoutes(
          stubCompletionService(Right(result)),
          stubCancellationService(Right(null)),
          authService
        )

        val request = Post(
          s"/consolidation-groups/${consolidationGroupId.value}/complete"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("completed")
          )
        }

      it("returns 404 when consolidation group not found"):
        val routes = ConsolidationGroupRoutes(
          stubCompletionService(
            Left(
              ConsolidationGroupCompletionError
                .ConsolidationGroupNotFound(
                  consolidationGroupId
                )
            )
          ),
          stubCancellationService(Right(null)),
          authService
        )

        val request = Post(
          s"/consolidation-groups/${consolidationGroupId.value}/complete"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it(
        "returns 409 when consolidation group is not assigned"
      ):
        val routes = ConsolidationGroupRoutes(
          stubCompletionService(
            Left(
              ConsolidationGroupCompletionError
                .ConsolidationGroupNotAssigned(
                  consolidationGroupId
                )
            )
          ),
          stubCancellationService(Right(null)),
          authService
        )

        val request = Post(
          s"/consolidation-groups/${consolidationGroupId.value}/complete"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }

    describe("DELETE /consolidation-groups/:id"):
      it("returns 200 with cancellation response on success"):
        val cancelled = ConsolidationGroup.Cancelled(
          consolidationGroupId,
          waveId,
          Nil
        )
        val cancelledEvent =
          ConsolidationGroupEvent.ConsolidationGroupCancelled(
            consolidationGroupId,
            waveId,
            at
          )
        val result = ConsolidationGroupCancellationResult(
          cancelled = cancelled,
          event = cancelledEvent
        )
        val routes = ConsolidationGroupRoutes(
          stubCompletionService(Right(null)),
          stubCancellationService(Right(result)),
          authService
        )

        val request = Delete(
          s"/consolidation-groups/${consolidationGroupId.value}"
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

      it("returns 404 when consolidation group not found"):
        val routes = ConsolidationGroupRoutes(
          stubCompletionService(Right(null)),
          stubCancellationService(
            Left(
              ConsolidationGroupCancellationError
                .ConsolidationGroupNotFound(
                  consolidationGroupId
                )
            )
          ),
          authService
        )

        val request = Delete(
          s"/consolidation-groups/${consolidationGroupId.value}"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 401 without session cookie"):
        val routes = ConsolidationGroupRoutes(
          stubCompletionService(Right(null)),
          stubCancellationService(Right(null)),
          authService
        )

        Delete(
          s"/consolidation-groups/${consolidationGroupId.value}"
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }
