package neon.app.http

import neon.common.WaveId
import neon.core.{AsyncWaveCancellationService, WaveCancellationError}

import io.circe.Json
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID

object WaveRoutes:

  def apply(
      waveCancellationService: AsyncWaveCancellationService
  ): Route =
    pathPrefix("waves"):
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
