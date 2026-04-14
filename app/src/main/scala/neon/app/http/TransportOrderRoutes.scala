package neon.app.http

import io.circe.Encoder
import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{Permission, TransportOrderId}
import neon.core.{
  AsyncTransportOrderCancellationService,
  AsyncTransportOrderConfirmationService,
  TransportOrderCancellationError,
  TransportOrderConfirmationError
}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

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
                  error match
                    case _: TransportOrderConfirmationError.TransportOrderNotFound =>
                      complete(StatusCodes.NotFound)
                    case _: TransportOrderConfirmationError.TransportOrderNotPending =>
                      complete(StatusCodes.Conflict)
                    case _: TransportOrderConfirmationError.HandlingUnitNotFound =>
                      complete(
                        StatusCodes.UnprocessableEntity
                      )
                    case _: TransportOrderConfirmationError.HandlingUnitNotPickCreated =>
                      complete(StatusCodes.Conflict)
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
                  error match
                    case _: TransportOrderCancellationError.TransportOrderNotFound =>
                      complete(StatusCodes.NotFound)
                    case _: TransportOrderCancellationError.TransportOrderAlreadyTerminal =>
                      complete(StatusCodes.Conflict)
      )
