package neon.app.http

import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{HandlingUnitId, OrderId, Permission, SlotId}
import neon.core.{AsyncSlotService, SlotError}
import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

object SlotRoutes:

  case class SlotReserveRequest(
      orderId: String,
      handlingUnitId: String
  ) derives Decoder

  case class SlotLifecycleResponse(
      status: String,
      slotId: String
  ) derives Encoder.AsObject

  def apply(
      slotService: AsyncSlotService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("slots"):
      AuthDirectives.requirePermission(
        Permission.SlotManage,
        authService
      ): _ =>
        concat(
          path(Segment / "reserve"): slotIdStr =>
            post:
              entity(as[SlotReserveRequest]): request =>
                val slotId =
                  SlotId(UUID.fromString(slotIdStr))
                val orderId = OrderId(
                  UUID.fromString(request.orderId)
                )
                val handlingUnitId = HandlingUnitId(
                  UUID.fromString(request.handlingUnitId)
                )
                onSuccess(
                  slotService.reserve(
                    slotId,
                    orderId,
                    handlingUnitId,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      SlotLifecycleResponse(
                        status = "reserved",
                        slotId = result.reserved.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "complete"): slotIdStr =>
            post:
              val slotId =
                SlotId(UUID.fromString(slotIdStr))
              onSuccess(
                slotService.complete(
                  slotId,
                  Instant.now()
                )
              ):
                case Right(result) =>
                  complete(
                    SlotLifecycleResponse(
                      status = "completed",
                      slotId = result.completed.id.value.toString
                    )
                  )
                case Left(error) =>
                  mapError(error)
          ,
          path(Segment / "release"): slotIdStr =>
            post:
              val slotId =
                SlotId(UUID.fromString(slotIdStr))
              onSuccess(
                slotService.release(
                  slotId,
                  Instant.now()
                )
              ):
                case Right(result) =>
                  complete(
                    SlotLifecycleResponse(
                      status = "released",
                      slotId = result.available.id.value.toString
                    )
                  )
                case Left(error) =>
                  mapError(error)
        )

  private def mapError(error: SlotError): Route =
    error match
      case _: SlotError.SlotNotFound =>
        complete(StatusCodes.NotFound)
      case _: SlotError.SlotInWrongState =>
        complete(StatusCodes.Conflict)
