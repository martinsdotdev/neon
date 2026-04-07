package neon.app.http

import neon.common.{CarrierId, LocationId, OrderId, WaveId}
import neon.core.{
  AsyncWaveCancellationService,
  AsyncWavePlanningService,
  DockCarrierAssignment,
  WaveCancellationError,
  WavePlanningError
}
import neon.order.AsyncOrderRepository
import neon.wave.OrderGrouping

import io.circe.{Decoder, Json}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID

import CirceSupport.given

object WaveRoutes:

  case class DockAssignmentDto(
      dockId: String,
      carrierId: String
  ) derives Decoder

  case class PlanAndReleaseRequest(
      orderIds: List[String],
      grouping: String,
      dockAssignments: List[DockAssignmentDto]
  ) derives Decoder

  def apply(
      waveCancellationService: AsyncWaveCancellationService,
      wavePlanningService: AsyncWavePlanningService,
      orderRepository: AsyncOrderRepository
  ): Route =
    pathPrefix("waves"):
      concat(
        path("plan-and-release"):
          post:
            entity(as[PlanAndReleaseRequest]): request =>
              import scala.concurrent.ExecutionContext.Implicits.global
              val orderIds =
                request.orderIds.map(s => OrderId(UUID.fromString(s)))
              val grouping = OrderGrouping.valueOf(request.grouping)
              val dockAssignments = request.dockAssignments.map { dto =>
                DockCarrierAssignment(
                  dockId = LocationId(UUID.fromString(dto.dockId)),
                  carrierId = CarrierId(UUID.fromString(dto.carrierId))
                )
              }
              onSuccess(
                orderRepository.findByIds(orderIds).flatMap { orders =>
                  wavePlanningService.planAndRelease(
                    orders,
                    grouping,
                    dockAssignments,
                    Instant.now()
                  )
                }
              ):
                case Right(result) =>
                  val json = Json.obj(
                    "status" -> Json.fromString("released"),
                    "waveId" -> Json.fromString(
                      result.wavePlan.wave.id.value.toString
                    ),
                    "tasksCreated" -> Json.fromInt(
                      result.release.tasks.size
                    ),
                    "consolidationGroupsCreated" -> Json.fromInt(
                      result.release.consolidationGroups.size
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
                    case _: WavePlanningError.DockConflict =>
                      complete(StatusCodes.Conflict)
                    case _ =>
                      complete(StatusCodes.UnprocessableEntity)
        ,
        path(Segment): waveIdStr =>
          delete:
            val waveId = WaveId(UUID.fromString(waveIdStr))
            onSuccess(
              waveCancellationService.cancel(waveId, Instant.now())
            ):
              case Right(result) =>
                val json = Json.obj(
                  "status" -> Json.fromString("cancelled"),
                  "waveId" -> Json.fromString(
                    result.cancelled.id.value.toString
                  ),
                  "cancelledTasks" -> Json.fromInt(
                    result.cancelledTasks.size
                  ),
                  "cancelledTransportOrders" -> Json.fromInt(
                    result.cancelledTransportOrders.size
                  ),
                  "cancelledConsolidationGroups" -> Json.fromInt(
                    result.cancelledConsolidationGroups.size
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
                  case _: WaveCancellationError.WaveNotFound =>
                    complete(StatusCodes.NotFound)
                  case _: WaveCancellationError.WaveAlreadyTerminal =>
                    complete(StatusCodes.Conflict)
      )
