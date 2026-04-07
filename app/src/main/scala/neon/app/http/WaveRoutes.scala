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
import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

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

  case class WaveReleaseResponse(
      status: String,
      waveId: String,
      tasksCreated: Int,
      consolidationGroupsCreated: Int
  ) derives Encoder.AsObject

  case class WaveCancellationResponse(
      status: String,
      waveId: String,
      cancelledTasks: Int,
      cancelledTransportOrders: Int,
      cancelledConsolidationGroups: Int
  ) derives Encoder.AsObject

  def apply(
      waveCancellationService: AsyncWaveCancellationService,
      wavePlanningService: AsyncWavePlanningService,
      orderRepository: AsyncOrderRepository
  )(using ExecutionContext): Route =
    pathPrefix("waves"):
      concat(
        path("plan-and-release"):
          post:
            entity(as[PlanAndReleaseRequest]): request =>
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
                  complete(
                    WaveReleaseResponse(
                      status = "released",
                      waveId = result.wavePlan.wave.id.value.toString,
                      tasksCreated = result.release.tasks.size,
                      consolidationGroupsCreated = result.release.consolidationGroups.size
                    )
                  )
                case Left(_: WavePlanningError.DockConflict) =>
                  complete(StatusCodes.Conflict)
                case Left(_) =>
                  complete(StatusCodes.UnprocessableEntity)
        ,
        path(Segment): waveIdStr =>
          delete:
            val waveId = WaveId(UUID.fromString(waveIdStr))
            onSuccess(
              waveCancellationService.cancel(waveId, Instant.now())
            ):
              case Right(result) =>
                complete(
                  WaveCancellationResponse(
                    status = "cancelled",
                    waveId = result.cancelled.id.value.toString,
                    cancelledTasks = result.cancelledTasks.size,
                    cancelledTransportOrders = result.cancelledTransportOrders.size,
                    cancelledConsolidationGroups = result.cancelledConsolidationGroups.size
                  )
                )
              case Left(_: WaveCancellationError.WaveNotFound) =>
                complete(StatusCodes.NotFound)
              case Left(_: WaveCancellationError.WaveAlreadyTerminal) =>
                complete(StatusCodes.Conflict)
      )
