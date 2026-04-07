package neon.app.http

import neon.common.ConsolidationGroupId
import neon.core.{AsyncWorkstationAssignmentService, WorkstationAssignmentError}
import io.circe.{Decoder, Json}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID

import CirceSupport.given

object WorkstationRoutes:

  case class AssignWorkstationRequest(
      consolidationGroupId: String
  ) derives Decoder

  def apply(
      assignmentService: AsyncWorkstationAssignmentService
  ): Route =
    pathPrefix("workstations"):
      path("assign"):
        post:
          entity(as[AssignWorkstationRequest]): request =>
            val cgId = ConsolidationGroupId(
              UUID.fromString(request.consolidationGroupId)
            )
            onSuccess(assignmentService.assign(cgId, Instant.now())):
              case Right(result) =>
                val json = Json.obj(
                  "status" -> Json.fromString("assigned"),
                  "consolidationGroupId" -> Json.fromString(
                    result.consolidationGroup.id.value.toString
                  ),
                  "workstationId" -> Json.fromString(
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
                  case _: WorkstationAssignmentError.ConsolidationGroupNotFound =>
                    complete(StatusCodes.NotFound)
                  case _: WorkstationAssignmentError.ConsolidationGroupNotReady =>
                    complete(StatusCodes.Conflict)
                  case _: WorkstationAssignmentError.NoWorkstationAvailable =>
                    complete(StatusCodes.ServiceUnavailable)
