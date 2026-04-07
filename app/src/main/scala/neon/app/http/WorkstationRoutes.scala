package neon.app.http

import neon.common.ConsolidationGroupId
import neon.core.{AsyncWorkstationAssignmentService, WorkstationAssignmentError}
import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID

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
      assignmentService: AsyncWorkstationAssignmentService
  ): Route =
    pathPrefix("workstations"):
      path("assign"):
        post:
          entity(as[AssignWorkstationRequest]): request =>
            val consolidationGroupId = ConsolidationGroupId(
              UUID.fromString(request.consolidationGroupId)
            )
            onSuccess(assignmentService.assign(consolidationGroupId, Instant.now())):
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
                    complete(StatusCodes.ServiceUnavailable)
