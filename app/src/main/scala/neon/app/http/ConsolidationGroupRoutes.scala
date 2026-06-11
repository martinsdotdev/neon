package neon.app.http

import io.circe.Encoder
import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{ConsolidationGroupId, Permission}
import neon.core.{
  AsyncConsolidationGroupCancellationService,
  AsyncConsolidationGroupCompletionService
}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given
import ProblemMapper.completeProblem

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

  case class ConsolidationGroupReadyResponse(
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
                  completeProblem(error)
        ,
        AuthDirectives.requirePermission(
          Permission.ConsolidationGroupAdvance,
          authService
        ): _ =>
          path(Segment / "ready-for-workstation"): consolidationGroupIdStr =>
            post:
              val id = ConsolidationGroupId(
                UUID.fromString(consolidationGroupIdStr)
              )
              onSuccess(
                completionService
                  .markReadyForWorkstation(id, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    ConsolidationGroupReadyResponse(
                      status = "ready-for-workstation",
                      consolidationGroupId = result.ready.id.value.toString
                    )
                  )
                case Left(error) =>
                  completeProblem(error)
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
                  completeProblem(error)
      )
