package neon.app.http

import neon.common.TransportOrderId
import neon.core.{AsyncTransportOrderConfirmationService, TransportOrderConfirmationError}
import io.circe.Json
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID

object TransportOrderRoutes:

  def apply(
      confirmationService: AsyncTransportOrderConfirmationService
  ): Route =
    pathPrefix("transport-orders"):
      path(Segment / "confirm"): transportOrderIdStr =>
        post:
          val id = TransportOrderId(UUID.fromString(transportOrderIdStr))
          onSuccess(confirmationService.confirm(id, Instant.now())):
            case Right(result) =>
              val json = Json.obj(
                "status" -> Json.fromString("confirmed"),
                "transportOrderId" -> Json.fromString(
                  result.confirmed.id.value.toString
                ),
                "handlingUnitInBuffer" -> Json.fromBoolean(true),
                "bufferCompletion" -> Json.fromBoolean(
                  result.bufferCompletion.isDefined
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
                case _: TransportOrderConfirmationError.TransportOrderNotFound =>
                  complete(StatusCodes.NotFound)
                case _: TransportOrderConfirmationError.TransportOrderNotPending =>
                  complete(StatusCodes.Conflict)
                case _: TransportOrderConfirmationError.HandlingUnitNotFound =>
                  complete(StatusCodes.UnprocessableEntity)
                case _: TransportOrderConfirmationError.HandlingUnitNotPickCreated =>
                  complete(StatusCodes.Conflict)
