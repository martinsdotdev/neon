package neon.app.http

import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{ConsolidationGroupId, Permission, WorkstationId}
import neon.core.{
  AsyncWorkstationAssignmentService,
  AsyncWorkstationLifecycleService,
  WorkstationAssignmentError,
  WorkstationLifecycleError
}
import neon.workstation.WorkstationType
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

  case class CreateWorkstationRequest(
      workstationType: String,
      slotCount: Int
  ) derives Decoder

  case class CreateWorkstationResponse(
      status: String,
      workstationId: String,
      workstationType: String,
      slotCount: Int
  ) derives Encoder.AsObject

  case class WorkstationLifecycleResponse(
      status: String,
      workstationId: String
  ) derives Encoder.AsObject

  def apply(
      assignmentService: AsyncWorkstationAssignmentService,
      lifecycleService: AsyncWorkstationLifecycleService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("workstations"):
      concat(
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
        ,
        AuthDirectives.requirePermission(
          Permission.WorkstationManage,
          authService
        ): _ =>
          concat(
            pathEnd:
              post:
                entity(as[CreateWorkstationRequest]): request =>
                  val workstationType =
                    WorkstationType.valueOf(
                      request.workstationType
                    )
                  onSuccess(
                    lifecycleService.create(
                      workstationType,
                      request.slotCount
                    )
                  ):
                    case Right(result) =>
                      complete(
                        CreateWorkstationResponse(
                          status = "created",
                          workstationId = result.workstation.id.value.toString,
                          workstationType = result.workstation.workstationType.toString,
                          slotCount = result.workstation.slotCount
                        )
                      )
                    case Left(_) =>
                      complete(
                        StatusCodes.UnprocessableEntity
                      )
            ,
            path(Segment / "enable"): workstationIdStr =>
              post:
                val id = WorkstationId(
                  UUID.fromString(workstationIdStr)
                )
                onSuccess(
                  lifecycleService
                    .enable(id, Instant.now())
                ):
                  case Right(result) =>
                    complete(
                      WorkstationLifecycleResponse(
                        status = "enabled",
                        workstationId = result.idle.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapLifecycleError(error)
            ,
            path(Segment / "disable"): workstationIdStr =>
              post:
                val id = WorkstationId(
                  UUID.fromString(workstationIdStr)
                )
                onSuccess(
                  lifecycleService
                    .disable(id, Instant.now())
                ):
                  case Right(result) =>
                    complete(
                      WorkstationLifecycleResponse(
                        status = "disabled",
                        workstationId = result.disabled.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapLifecycleError(error)
          )
      )

  private def mapLifecycleError(
      error: WorkstationLifecycleError
  ): Route =
    error match
      case _: WorkstationLifecycleError.WorkstationNotFound =>
        complete(StatusCodes.NotFound)
      case _: WorkstationLifecycleError.WorkstationInWrongState =>
        complete(StatusCodes.Conflict)
