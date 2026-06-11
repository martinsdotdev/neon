package neon.app.http

import io.circe.Encoder
import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{Permission, TransportOrderId}
import neon.core.{AsyncTransportOrderCancellationService, AsyncTransportOrderConfirmationService}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given
import ProblemMapper.completeProblem

object TransportOrderRoutes:

  case class TransportOrderConfirmationResponse(
      status: String,
      transportOrderId: String,
      handlingUnitInBuffer: Boolean,
      bufferCompletion: Boolean
  ) derives Encoder.AsObject

  case class TransportOrderCancellationResponse(
      status: String,
      transportOrderId: String
  ) derives Encoder.AsObject

  def apply(
      confirmationService: AsyncTransportOrderConfirmationService,
      cancellationService: AsyncTransportOrderCancellationService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("transport-orders"):
      concat(
        AuthDirectives.requirePermission(
          Permission.TransportOrderConfirm,
          authService
        ): _ =>
          path(Segment / "confirm"): transportOrderIdStr =>
            post:
              val id = TransportOrderId(
                UUID.fromString(transportOrderIdStr)
              )
              onSuccess(
                confirmationService
                  .confirm(id, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    TransportOrderConfirmationResponse(
                      status = "confirmed",
                      transportOrderId = result.confirmed.id.value.toString,
                      handlingUnitInBuffer = true,
                      bufferCompletion = result.bufferCompletion.isDefined
                    )
                  )
                case Left(error) =>
                  completeProblem(error)
        ,
        AuthDirectives.requirePermission(
          Permission.TransportOrderCancel,
          authService
        ): _ =>
          path(Segment): transportOrderIdStr =>
            delete:
              val id = TransportOrderId(
                UUID.fromString(transportOrderIdStr)
              )
              onSuccess(
                cancellationService
                  .cancel(id, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    TransportOrderCancellationResponse(
                      status = "cancelled",
                      transportOrderId = result.cancelled.id.value.toString
                    )
                  )
                case Left(error) =>
                  completeProblem(error)
      )
