package neon.app.http

import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{ConsolidationGroupId, Permission}
import neon.core.{AsyncConsolidationGroupCompletionService, ConsolidationGroupCompletionError}
import io.circe.Encoder
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

object ConsolidationGroupRoutes:

  case class ConsolidationGroupCompletionResponse(
      status: String,
      consolidationGroupId: String,
      workstationReleased: String
  ) derives Encoder.AsObject

  def apply(
      completionService: AsyncConsolidationGroupCompletionService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("consolidation-groups"):
      AuthDirectives.requirePermission(
        Permission.ConsolidationGroupComplete,
        authService
      ): _ =>
        path(Segment / "complete"): consolidationGroupIdStr =>
          post:
            val id = ConsolidationGroupId(
              UUID.fromString(consolidationGroupIdStr)
            )
            onSuccess(
              completionService
                .complete(id, Instant.now())
            ):
              case Right(result) =>
                complete(
                  ConsolidationGroupCompletionResponse(
                    status = "completed",
                    consolidationGroupId = result.completed.id.value.toString,
                    workstationReleased = result.workstation.id.value.toString
                  )
                )
              case Left(error) =>
                error match
                  case _: ConsolidationGroupCompletionError.ConsolidationGroupNotFound =>
                    complete(StatusCodes.NotFound)
                  case _: ConsolidationGroupCompletionError.ConsolidationGroupNotAssigned =>
                    complete(StatusCodes.Conflict)
                  case _: ConsolidationGroupCompletionError.WorkstationNotFound =>
                    complete(
                      StatusCodes.UnprocessableEntity
                    )
                  case _: ConsolidationGroupCompletionError.WorkstationNotActive =>
                    complete(StatusCodes.Conflict)
