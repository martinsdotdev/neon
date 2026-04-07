package neon.app.http

import neon.common.TaskId
import neon.core.{AsyncTaskCompletionService, TaskCompletionError}

import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID

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

  def apply(
      taskCompletionService: AsyncTaskCompletionService
  ): Route =
    pathPrefix("tasks"):
      path(Segment / "complete"): taskIdStr =>
        post:
          entity(as[CompleteTaskRequest]): request =>
            val taskId = TaskId(UUID.fromString(taskIdStr))
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
                    complete(StatusCodes.UnprocessableEntity)
                  case _: TaskCompletionError.VerificationRequired =>
                    complete(StatusCodes.PreconditionRequired)
