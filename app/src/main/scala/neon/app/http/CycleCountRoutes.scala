package neon.app.http

import io.circe.{Decoder, Encoder}
import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{
  CountMethod,
  CountTaskId,
  CountType,
  CycleCountId,
  Permission,
  SkuId,
  UserId,
  WarehouseAreaId
}
import neon.core.{AsyncCycleCountService, CycleCountError}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

object CycleCountRoutes:

  case class CreateCycleCountRequest(
      warehouseAreaId: String,
      skuIds: List[String],
      countType: String,
      countMethod: String
  ) derives Decoder

  case class AssignCountTaskRequest(
      userId: String
  ) derives Decoder

  case class RecordCountRequest(
      actualQuantity: Int
  ) derives Decoder

  case class CycleCountResponse(
      status: String,
      cycleCountId: String
  ) derives Encoder.AsObject

  case class CountTaskResponse(
      status: String,
      countTaskId: String
  ) derives Encoder.AsObject

  def apply(
      cycleCountService: AsyncCycleCountService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("cycle-counts"):
      AuthDirectives.requirePermission(
        Permission.CycleCountManage,
        authService
      ): _ =>
        concat(
          pathEnd:
            post:
              entity(as[CreateCycleCountRequest]): request =>
                val warehouseAreaId = WarehouseAreaId(
                  UUID.fromString(request.warehouseAreaId)
                )
                val skuIds = request.skuIds.map(id => SkuId(UUID.fromString(id)))
                val countType =
                  CountType.valueOf(request.countType)
                val countMethod =
                  CountMethod.valueOf(request.countMethod)
                onSuccess(
                  cycleCountService.create(
                    warehouseAreaId,
                    skuIds,
                    countType,
                    countMethod,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      CycleCountResponse(
                        status = "created",
                        cycleCountId = result.cycleCount.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "start"): idStr =>
            post:
              val id = CycleCountId(
                UUID.fromString(idStr)
              )
              onSuccess(
                cycleCountService.start(
                  id,
                  Instant.now()
                )
              ):
                case Right(result) =>
                  complete(
                    CycleCountResponse(
                      status = "started",
                      cycleCountId = result.cycleCount.id.value.toString
                    )
                  )
                case Left(error) =>
                  mapError(error)
          ,
          path(Segment / "tasks" / Segment / "assign"): (_, taskIdStr) =>
            post:
              entity(as[AssignCountTaskRequest]): request =>
                val taskId = CountTaskId(
                  UUID.fromString(taskIdStr)
                )
                val userId = UserId(
                  UUID.fromString(request.userId)
                )
                onSuccess(
                  cycleCountService.assignCountTask(
                    taskId,
                    userId,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      CountTaskResponse(
                        status = "assigned",
                        countTaskId = result.countTask.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "tasks" / Segment / "record"): (_, taskIdStr) =>
            post:
              entity(as[RecordCountRequest]): request =>
                val taskId = CountTaskId(
                  UUID.fromString(taskIdStr)
                )
                onSuccess(
                  cycleCountService.recordCount(
                    taskId,
                    request.actualQuantity,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      CountTaskResponse(
                        status = "recorded",
                        countTaskId = result.countTask.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
        )

  private def mapError(error: CycleCountError): Route =
    error match
      case _: CycleCountError.CycleCountNotFound =>
        complete(StatusCodes.NotFound)
      case _: CycleCountError.CycleCountInWrongState =>
        complete(StatusCodes.Conflict)
      case _: CycleCountError.CountTaskNotFound =>
        complete(StatusCodes.NotFound)
      case _: CycleCountError.CountTaskInWrongState =>
        complete(StatusCodes.Conflict)
