package neon.app.http

import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{ConsolidationGroupId, Permission}
import neon.core.{AsyncWorkstationAssignmentService, WorkstationAssignmentError}
import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

object WorkstationRoutes:

  case class AssignWorkstationRequest(
      consolidationGroupId: String
  ) derives Decoder

  case class WorkstationAssignmentResponse(
      status: String,
      consolidationGroupId: String,
      workstationId: String
  ) derives Encoder.AsObject

  def apply(
      assignmentService: AsyncWorkstationAssignmentService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("workstations"):
      AuthDirectives.requirePermission(
        Permission.WorkstationAssign,
        authService
      ): _ =>
        path("assign"):
          post:
            entity(as[AssignWorkstationRequest]): request =>
              val consolidationGroupId =
                ConsolidationGroupId(
                  UUID.fromString(
                    request.consolidationGroupId
                  )
                )
              onSuccess(
                assignmentService.assign(
                  consolidationGroupId,
                  Instant.now()
                )
              ):
                case Right(result) =>
                  complete(
                    WorkstationAssignmentResponse(
                      status = "assigned",
                      consolidationGroupId = result.consolidationGroup.id.value.toString,
                      workstationId = result.workstation.id.value.toString
                    )
                  )
                case Left(error) =>
                  error match
                    case _: WorkstationAssignmentError.ConsolidationGroupNotFound =>
                      complete(StatusCodes.NotFound)
                    case _: WorkstationAssignmentError.ConsolidationGroupNotReady =>
                      complete(StatusCodes.Conflict)
                    case _: WorkstationAssignmentError.NoWorkstationAvailable =>
                      complete(
                        StatusCodes.ServiceUnavailable
                      )
