package neon.app.http

import io.circe.Encoder
import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{ConsolidationGroupId, Permission}
import neon.core.{
  AsyncConsolidationGroupCancellationService,
  AsyncConsolidationGroupCompletionService,
  ConsolidationGroupCancellationError,
  ConsolidationGroupCompletionError
}
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

  case class ConsolidationGroupCancellationResponse(
      status: String,
      consolidationGroupId: String
  ) derives Encoder.AsObject

  def apply(
      completionService: AsyncConsolidationGroupCompletionService,
      cancellationService: AsyncConsolidationGroupCancellationService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("consolidation-groups"):
      concat(
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
        ,
        AuthDirectives.requirePermission(
          Permission.ConsolidationGroupCancel,
          authService
        ): _ =>
          path(Segment): consolidationGroupIdStr =>
            delete:
              val id = ConsolidationGroupId(
                UUID.fromString(consolidationGroupIdStr)
              )
              onSuccess(
                cancellationService
                  .cancel(id, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    ConsolidationGroupCancellationResponse(
                      status = "cancelled",
                      consolidationGroupId = result.cancelled.id.value.toString
                    )
                  )
                case Left(error) =>
                  error match
                    case _: ConsolidationGroupCancellationError.ConsolidationGroupNotFound =>
                      complete(StatusCodes.NotFound)
                    case _: ConsolidationGroupCancellationError.ConsolidationGroupAlreadyTerminal =>
                      complete(StatusCodes.Conflict)
      )
