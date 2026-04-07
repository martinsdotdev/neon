package neon.app.http

import neon.common.{
  HandlingUnitId,
  LocationId,
  OrderId,
  PackagingLevel,
  SkuId,
  TaskId,
  UserId,
  WaveId
}
import neon.core.{
  AsyncTaskCompletionService,
  TaskCompletionError,
  TaskCompletionResult,
  VerificationProfile
}
import neon.task.{AsyncTaskRepository, Task, TaskEvent, TaskType}
import io.circe.parser.parse
import io.circe.{Decoder, Json}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.Future

class TaskRoutesSuite extends AnyFunSpec with ScalatestRouteTest:

  private val taskId = TaskId()
  private val skuId = SkuId()
  private val orderId = OrderId()
  private val waveId = WaveId()
  private val userId = UserId()
  private val srcLoc = LocationId()
  private val dstLoc = LocationId()
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
    srcLoc,
    dstLoc,
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
    srcLoc,
    dstLoc,
    10,
    10,
    userId,
    at
  )

  private def stubService(
      result: Either[TaskCompletionError, TaskCompletionResult]
  ): AsyncTaskCompletionService =
    val stubRepo = new AsyncTaskRepository:
      def findById(id: TaskId) = Future.successful(None)
      def findByWaveId(waveId: WaveId) = Future.successful(Nil)
      def findByHandlingUnitId(id: HandlingUnitId) = Future.successful(Nil)
      def save(task: Task, event: TaskEvent) = Future.successful(())
      def saveAll(entries: List[(Task, TaskEvent)]) = Future.successful(())

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
      ): Future[Either[TaskCompletionError, TaskCompletionResult]] =
        Future.successful(result)

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
        val routes = TaskRoutes(stubService(Right(result)))
        val body =
          s"""{"actualQuantity": 10, "verified": true}"""

        Post(
          s"/tasks/${taskId.value}/complete",
          HttpEntity(ContentTypes.`application/json`, body)
        ) ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json = parse(responseAs[String]).getOrElse(Json.Null)
          assert(json.hcursor.get[String]("status").contains("completed"))
        }

      it("returns 404 when task not found"):
        val routes = TaskRoutes(
          stubService(Left(TaskCompletionError.TaskNotFound(taskId)))
        )
        val body =
          s"""{"actualQuantity": 10, "verified": true}"""

        Post(
          s"/tasks/${taskId.value}/complete",
          HttpEntity(ContentTypes.`application/json`, body)
        ) ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when task is not in Assigned state"):
        val routes = TaskRoutes(
          stubService(Left(TaskCompletionError.TaskNotAssigned(taskId)))
        )
        val body =
          s"""{"actualQuantity": 10, "verified": true}"""

        Post(
          s"/tasks/${taskId.value}/complete",
          HttpEntity(ContentTypes.`application/json`, body)
        ) ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }
