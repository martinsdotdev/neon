package neon.app.http

import neon.app.auth.*
import neon.common.{
  HandlingUnitId,
  LocationId,
  OrderId,
  PackagingLevel,
  Permission,
  Role,
  SkuId,
  TaskId,
  UserId,
  WaveId
}
import neon.core.{
  AsyncTaskCompletionService,
  AsyncTaskLifecycleService,
  TaskCompletionError,
  TaskCompletionResult,
  VerificationProfile
}
import neon.task.{AsyncTaskRepository, Task, TaskEvent, TaskType}
import neon.user.{AsyncUserRepository, User}
import io.circe.parser.parse
import io.circe.{Decoder, Json}
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class TaskRoutesSuite extends AnyFunSpec with ScalatestRouteTest:

  private val taskId = TaskId()
  private val skuId = SkuId()
  private val orderId = OrderId()
  private val waveId = WaveId()
  private val userId = UserId()
  private val sourceLocationId = LocationId()
  private val destinationLocationId = LocationId()
  private val at = Instant.now()

  private val completedTask = Task.Completed(
    taskId,
    TaskType.Pick,
    skuId,
    PackagingLevel.Each,
    10,
    10,
    orderId,
    Some(waveId),
    None,
    None,
    None,
    sourceLocationId,
    destinationLocationId,
    userId
  )

  private val completedEvent = TaskEvent.TaskCompleted(
    taskId,
    TaskType.Pick,
    skuId,
    PackagingLevel.Each,
    Some(waveId),
    None,
    None,
    sourceLocationId,
    destinationLocationId,
    10,
    10,
    userId,
    at
  )

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
      result: Either[TaskCompletionError, TaskCompletionResult]
  ): AsyncTaskCompletionService =
    val stubRepo = new AsyncTaskRepository:
      def findById(id: TaskId) = Future.successful(None)
      def findByWaveId(waveId: WaveId) =
        Future.successful(Nil)
      def findByHandlingUnitId(id: HandlingUnitId) =
        Future.successful(Nil)
      def save(task: Task, event: TaskEvent) =
        Future.successful(())
      def saveAll(entries: List[(Task, TaskEvent)]) =
        Future.successful(())

    new AsyncTaskCompletionService(
      stubRepo,
      null,
      null,
      null,
      VerificationProfile.disabled
    ):
      override def complete(
          taskId: TaskId,
          actualQuantity: Int,
          verified: Boolean,
          at: Instant
      ): Future[
        Either[TaskCompletionError, TaskCompletionResult]
      ] =
        Future.successful(result)

  private val stubLifecycleService =
    val stubRepo = new AsyncTaskRepository:
      def findById(id: TaskId) = Future.successful(None)
      def findByWaveId(waveId: WaveId) =
        Future.successful(Nil)
      def findByHandlingUnitId(id: HandlingUnitId) =
        Future.successful(Nil)
      def save(task: Task, event: TaskEvent) =
        Future.successful(())
      def saveAll(entries: List[(Task, TaskEvent)]) =
        Future.successful(())
    val stubUserRepo = new AsyncUserRepository:
      def findById(id: UserId) = Future.successful(None)
      def findByLogin(login: String) =
        Future.successful(None)
    AsyncTaskLifecycleService(stubRepo, stubUserRepo)

  describe("TaskRoutes"):
    describe("POST /tasks/:id/complete"):
      it("returns 200 with completed task on success"):
        val result = TaskCompletionResult(
          completed = completedTask,
          completedEvent = completedEvent,
          shortpick = None,
          transportOrder = None,
          waveCompletion = None,
          pickingCompletion = None
        )
        val routes =
          TaskRoutes(stubService(Right(result)), stubLifecycleService, authService)
        val body =
          s"""{"actualQuantity": 10, "verified": true}"""

        val request = Post(
          s"/tasks/${taskId.value}/complete",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json = parse(responseAs[String]).getOrElse(Json.Null)
          assert(json.hcursor.get[String]("status").contains("completed"))
        }

      it("returns 404 when task not found"):
        val routes = TaskRoutes(
          stubService(
            Left(TaskCompletionError.TaskNotFound(taskId))
          ),
          stubLifecycleService,
          authService
        )
        val body =
          s"""{"actualQuantity": 10, "verified": true}"""

        val request = Post(
          s"/tasks/${taskId.value}/complete",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when task is not in Assigned state"):
        val routes = TaskRoutes(
          stubService(
            Left(
              TaskCompletionError.TaskNotAssigned(taskId)
            )
          ),
          stubLifecycleService,
          authService
        )
        val body =
          s"""{"actualQuantity": 10, "verified": true}"""

        val request = Post(
          s"/tasks/${taskId.value}/complete",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }

      it("returns 401 without session cookie"):
        val routes =
          TaskRoutes(stubService(Right(null)), stubLifecycleService, authService)
        val body =
          s"""{"actualQuantity": 10, "verified": true}"""
        Post(
          s"/tasks/${taskId.value}/complete",
          HttpEntity(ContentTypes.`application/json`, body)
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }
