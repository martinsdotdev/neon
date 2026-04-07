package neon.app.http

import neon.common.TaskId
import neon.core.{AsyncTaskCompletionService, TaskCompletionError}
import io.circe.{Decoder, Json}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
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
                val json = Json
                  .obj(
                    "status" -> Json.fromString("completed"),
                    "taskId" -> Json.fromString(
                      result.completed.id.value.toString
                    ),
                    "actualQuantity" -> Json.fromInt(
                      result.completed.actualQuantity
                    ),
                    "requestedQuantity" -> Json.fromInt(
                      result.completed.requestedQuantity
                    ),
                    "hasShortpick" -> Json.fromBoolean(
                      result.shortpick.isDefined
                    ),
                    "hasTransportOrder" -> Json.fromBoolean(
                      result.transportOrder.isDefined
                    )
                  )
                complete(
                  HttpEntity(
                    ContentTypes.`application/json`,
                    json.noSpaces
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
