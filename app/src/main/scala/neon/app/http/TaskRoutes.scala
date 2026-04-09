package neon.app.http

import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{LocationId, Permission, TaskId, UserId}
import neon.core.{
  AsyncTaskCompletionService,
  AsyncTaskLifecycleService,
  TaskCompletionError,
  TaskLifecycleError
}
import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

object TaskRoutes:

  case class CompleteTaskRequest(
      actualQuantity: Int,
      verified: Boolean
  ) derives Decoder

  case class TaskCompletionResponse(
      status: String,
      taskId: String,
      actualQuantity: Int,
      requestedQuantity: Int,
      hasShortpick: Boolean,
      hasTransportOrder: Boolean
  ) derives Encoder.AsObject

  case class AllocateTaskRequest(
      sourceLocationId: String,
      destinationLocationId: String
  ) derives Decoder

  case class AllocateTaskResponse(
      status: String,
      taskId: String,
      sourceLocationId: String,
      destinationLocationId: String
  ) derives Encoder.AsObject

  case class AssignTaskRequest(userId: String) derives Decoder

  case class AssignTaskResponse(
      status: String,
      taskId: String,
      userId: String
  ) derives Encoder.AsObject

  case class CancelTaskResponse(
      status: String,
      taskId: String
  ) derives Encoder.AsObject

  def apply(
      taskCompletionService: AsyncTaskCompletionService,
      taskLifecycleService: AsyncTaskLifecycleService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("tasks"):
      concat(
        AuthDirectives.requirePermission(
          Permission.TaskComplete,
          authService
        ): _ =>
          path(Segment / "complete"): taskIdStr =>
            post:
              entity(as[CompleteTaskRequest]): request =>
                val taskId =
                  TaskId(UUID.fromString(taskIdStr))
                onSuccess(
                  taskCompletionService.complete(
                    taskId,
                    request.actualQuantity,
                    request.verified,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      TaskCompletionResponse(
                        status = "completed",
                        taskId = result.completed.id.value.toString,
                        actualQuantity = result.completed.actualQuantity,
                        requestedQuantity = result.completed.requestedQuantity,
                        hasShortpick = result.shortpick.isDefined,
                        hasTransportOrder = result.transportOrder.isDefined
                      )
                    )
                  case Left(error) =>
                    error match
                      case _: TaskCompletionError.TaskNotFound =>
                        complete(StatusCodes.NotFound)
                      case _: TaskCompletionError.TaskNotAssigned =>
                        complete(StatusCodes.Conflict)
                      case _: TaskCompletionError.InvalidActualQuantity =>
                        complete(
                          StatusCodes.UnprocessableEntity
                        )
                      case _: TaskCompletionError.VerificationRequired =>
                        complete(
                          StatusCodes.PreconditionRequired
                        )
        ,
        AuthDirectives.requirePermission(
          Permission.TaskAllocate,
          authService
        ): _ =>
          path(Segment / "allocate"): taskIdStr =>
            post:
              entity(as[AllocateTaskRequest]): request =>
                val taskId =
                  TaskId(UUID.fromString(taskIdStr))
                val sourceLocationId = LocationId(
                  UUID.fromString(request.sourceLocationId)
                )
                val destinationLocationId = LocationId(
                  UUID.fromString(
                    request.destinationLocationId
                  )
                )
                onSuccess(
                  taskLifecycleService.allocate(
                    taskId,
                    sourceLocationId,
                    destinationLocationId,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      AllocateTaskResponse(
                        status = "allocated",
                        taskId = result.allocated.id.value.toString,
                        sourceLocationId = result.allocated.sourceLocationId.value.toString,
                        destinationLocationId =
                          result.allocated.destinationLocationId.value.toString
                      )
                    )
                  case Left(error) =>
                    mapLifecycleError(error)
        ,
        AuthDirectives.requirePermission(
          Permission.TaskAssign,
          authService
        ): _ =>
          path(Segment / "assign"): taskIdStr =>
            post:
              entity(as[AssignTaskRequest]): request =>
                val taskId =
                  TaskId(UUID.fromString(taskIdStr))
                val userId = UserId(
                  UUID.fromString(request.userId)
                )
                onSuccess(
                  taskLifecycleService.assign(
                    taskId,
                    userId,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      AssignTaskResponse(
                        status = "assigned",
                        taskId = result.assigned.id.value.toString,
                        userId = result.assigned.assignedTo.value.toString
                      )
                    )
                  case Left(error) =>
                    mapLifecycleError(error)
        ,
        AuthDirectives.requirePermission(
          Permission.TaskCancel,
          authService
        ): _ =>
          path(Segment): taskIdStr =>
            delete:
              val taskId =
                TaskId(UUID.fromString(taskIdStr))
              onSuccess(
                taskLifecycleService
                  .cancel(taskId, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    CancelTaskResponse(
                      status = "cancelled",
                      taskId = result.cancelled.id.value.toString
                    )
                  )
                case Left(error) =>
                  mapLifecycleError(error)
      )

  private def mapLifecycleError(
      error: TaskLifecycleError
  ): Route =
    error match
      case _: TaskLifecycleError.TaskNotFound =>
        complete(StatusCodes.NotFound)
      case _: TaskLifecycleError.TaskInWrongState =>
        complete(StatusCodes.Conflict)
      case _: TaskLifecycleError.TaskAlreadyTerminal =>
        complete(StatusCodes.Conflict)
      case _: TaskLifecycleError.UserNotFound =>
        complete(StatusCodes.UnprocessableEntity)
      case _: TaskLifecycleError.UserNotActive =>
        complete(StatusCodes.UnprocessableEntity)
