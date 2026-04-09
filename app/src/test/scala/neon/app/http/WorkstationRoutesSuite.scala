package neon.app.http

import neon.app.auth.*
import neon.common.{
  ConsolidationGroupId,
  Permission,
  Role,
  UserId,
  WaveId,
  WorkstationId
}
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent
}
import neon.core.{
  AsyncWorkstationAssignmentService,
  AsyncWorkstationLifecycleService,
  WorkstationAssignmentError,
  WorkstationAssignmentResult,
  WorkstationCreateResult,
  WorkstationDisableResult,
  WorkstationEnableResult,
  WorkstationLifecycleError
}
import neon.user.User
import neon.workstation.{Workstation, WorkstationEvent, WorkstationType}
import io.circe.Json
import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  StatusCodes
}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class WorkstationRoutesSuite
    extends AnyFunSpec
    with ScalatestRouteTest:

  private val workstationId = WorkstationId()
  private val consolidationGroupId = ConsolidationGroupId()
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

  private def stubAssignmentService(
      result: Either[
        WorkstationAssignmentError,
        WorkstationAssignmentResult
      ]
  ): AsyncWorkstationAssignmentService =
    new AsyncWorkstationAssignmentService(null, null):
      override def assign(
          consolidationGroupId: ConsolidationGroupId,
          at: Instant
      ): Future[
        Either[
          WorkstationAssignmentError,
          WorkstationAssignmentResult
        ]
      ] =
        Future.successful(result)

  private def stubLifecycleService(
      createResult: Either[
        WorkstationLifecycleError,
        WorkstationCreateResult
      ],
      enableResult: Either[
        WorkstationLifecycleError,
        WorkstationEnableResult
      ] = Left(
        WorkstationLifecycleError.WorkstationNotFound(
          WorkstationId()
        )
      )
  ): AsyncWorkstationLifecycleService =
    new AsyncWorkstationLifecycleService(null):
      override def create(
          workstationType: WorkstationType,
          slotCount: Int
      ): Future[
        Either[
          WorkstationLifecycleError,
          WorkstationCreateResult
        ]
      ] =
        Future.successful(createResult)

      override def enable(
          workstationId: WorkstationId,
          at: Instant
      ): Future[
        Either[
          WorkstationLifecycleError,
          WorkstationEnableResult
        ]
      ] =
        Future.successful(enableResult)

      override def disable(
          workstationId: WorkstationId,
          at: Instant
      ): Future[
        Either[
          WorkstationLifecycleError,
          WorkstationDisableResult
        ]
      ] =
        Future.successful(
          Left(
            WorkstationLifecycleError.WorkstationNotFound(
              workstationId
            )
          )
        )

  describe("WorkstationRoutes"):
    describe("POST /workstations/assign"):
      it("returns 200 with assignment response on success"):
        val assigned = ConsolidationGroup.Assigned(
          consolidationGroupId,
          waveId,
          Nil,
          workstationId
        )
        val groupEvent =
          ConsolidationGroupEvent.ConsolidationGroupAssigned(
            consolidationGroupId,
            waveId,
            workstationId,
            at
          )
        val active = Workstation.Active(
          workstationId,
          WorkstationType.PutWall,
          8,
          consolidationGroupId
        )
        val workstationEvent =
          WorkstationEvent.WorkstationAssigned(
            workstationId,
            WorkstationType.PutWall,
            consolidationGroupId,
            at
          )
        val result = WorkstationAssignmentResult(
          consolidationGroup = assigned,
          consolidationGroupEvent = groupEvent,
          workstation = active,
          workstationEvent = workstationEvent
        )
        val routes = WorkstationRoutes(
          stubAssignmentService(Right(result)),
          stubLifecycleService(Right(null)),
          authService
        )
        val body = s"""{
          |"consolidationGroupId": "${consolidationGroupId.value}"
          |}""".stripMargin

        val request = Post(
          "/workstations/assign",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("assigned")
          )
        }

      it(
        "returns 404 when consolidation group not found"
      ):
        val routes = WorkstationRoutes(
          stubAssignmentService(
            Left(
              WorkstationAssignmentError
                .ConsolidationGroupNotFound(
                  consolidationGroupId
                )
            )
          ),
          stubLifecycleService(Right(null)),
          authService
        )
        val body = s"""{
          |"consolidationGroupId": "${consolidationGroupId.value}"
          |}""".stripMargin

        val request = Post(
          "/workstations/assign",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when consolidation group not ready"):
        val routes = WorkstationRoutes(
          stubAssignmentService(
            Left(
              WorkstationAssignmentError
                .ConsolidationGroupNotReady(
                  consolidationGroupId
                )
            )
          ),
          stubLifecycleService(Right(null)),
          authService
        )
        val body = s"""{
          |"consolidationGroupId": "${consolidationGroupId.value}"
          |}""".stripMargin

        val request = Post(
          "/workstations/assign",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }

      it(
        "returns 503 when no workstation available"
      ):
        val routes = WorkstationRoutes(
          stubAssignmentService(
            Left(
              WorkstationAssignmentError
                .NoWorkstationAvailable(
                  consolidationGroupId
                )
            )
          ),
          stubLifecycleService(Right(null)),
          authService
        )
        val body = s"""{
          |"consolidationGroupId": "${consolidationGroupId.value}"
          |}""".stripMargin

        val request = Post(
          "/workstations/assign",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.ServiceUnavailable)
        }

    describe("POST /workstations"):
      it("returns 200 with created response on success"):
        val disabled = Workstation.Disabled(
          workstationId,
          WorkstationType.PutWall,
          8
        )
        val result = WorkstationCreateResult(disabled)
        val routes = WorkstationRoutes(
          stubAssignmentService(Right(null)),
          stubLifecycleService(Right(result)),
          authService
        )
        val body = s"""{
          |"workstationType": "PutWall",
          |"slotCount": 8
          |}""".stripMargin

        val request = Post(
          "/workstations",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("created")
          )
        }

      it("returns 401 without session cookie"):
        val routes = WorkstationRoutes(
          stubAssignmentService(Right(null)),
          stubLifecycleService(Right(null)),
          authService
        )
        val body = s"""{
          |"workstationType": "PutWall",
          |"slotCount": 8
          |}""".stripMargin

        Post(
          "/workstations",
          HttpEntity(ContentTypes.`application/json`, body)
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }

    describe("POST /workstations/:id/enable"):
      it("returns 200 with enabled response on success"):
        val idle = Workstation.Idle(
          workstationId,
          WorkstationType.PutWall,
          8
        )
        val event = WorkstationEvent.WorkstationEnabled(
          workstationId,
          WorkstationType.PutWall,
          8,
          at
        )
        val result = WorkstationEnableResult(idle, event)
        val routes = WorkstationRoutes(
          stubAssignmentService(Right(null)),
          stubLifecycleService(
            Right(null),
            enableResult = Right(result)
          ),
          authService
        )

        val request = Post(
          s"/workstations/${workstationId.value}/enable"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("enabled")
          )
        }

      it("returns 404 when workstation not found"):
        val routes = WorkstationRoutes(
          stubAssignmentService(Right(null)),
          stubLifecycleService(
            Right(null),
            enableResult = Left(
              WorkstationLifecycleError
                .WorkstationNotFound(workstationId)
            )
          ),
          authService
        )

        val request = Post(
          s"/workstations/${workstationId.value}/enable"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when workstation is in wrong state"):
        val routes = WorkstationRoutes(
          stubAssignmentService(Right(null)),
          stubLifecycleService(
            Right(null),
            enableResult = Left(
              WorkstationLifecycleError
                .WorkstationInWrongState(workstationId)
            )
          ),
          authService
        )

        val request = Post(
          s"/workstations/${workstationId.value}/enable"
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }
