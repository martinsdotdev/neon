package neon.app.http

import neon.common.ConsolidationGroupId
import neon.core.{AsyncConsolidationGroupCompletionService, ConsolidationGroupCompletionError}
import io.circe.Json
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID

object ConsolidationGroupRoutes:

  def apply(
      completionService: AsyncConsolidationGroupCompletionService
  ): Route =
    pathPrefix("consolidation-groups"):
      path(Segment / "complete"): consolidationGroupIdStr =>
        post:
          val id =
            ConsolidationGroupId(UUID.fromString(consolidationGroupIdStr))
          onSuccess(completionService.complete(id, Instant.now())):
            case Right(result) =>
              val json = Json.obj(
                "status" -> Json.fromString("completed"),
                "consolidationGroupId" -> Json.fromString(
                  result.completed.id.value.toString
                ),
                "workstationReleased" -> Json.fromString(
                  result.workstation.id.value.toString
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
                case _: ConsolidationGroupCompletionError.ConsolidationGroupNotFound =>
                  complete(StatusCodes.NotFound)
                case _: ConsolidationGroupCompletionError.ConsolidationGroupNotAssigned =>
                  complete(StatusCodes.Conflict)
                case _: ConsolidationGroupCompletionError.WorkstationNotFound =>
                  complete(StatusCodes.UnprocessableEntity)
                case _: ConsolidationGroupCompletionError.WorkstationNotActive =>
                  complete(StatusCodes.Conflict)
