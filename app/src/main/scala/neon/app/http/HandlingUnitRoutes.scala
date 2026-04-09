package neon.app.http

import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{HandlingUnitId, Permission}
import neon.core.{AsyncHandlingUnitLifecycleService, HandlingUnitLifecycleError}
import io.circe.Encoder
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

object HandlingUnitRoutes:

  case class HandlingUnitLifecycleResponse(
      status: String,
      handlingUnitId: String
  ) derives Encoder.AsObject

  def apply(
      lifecycleService: AsyncHandlingUnitLifecycleService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("handling-units"):
      AuthDirectives.requirePermission(
        Permission.HandlingUnitManage,
        authService
      ): _ =>
        concat(
          path(Segment / "pack"): idStr =>
            post:
              val id = HandlingUnitId(UUID.fromString(idStr))
              onSuccess(
                lifecycleService.pack(id, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    HandlingUnitLifecycleResponse(
                      status = "packed",
                      handlingUnitId = result.packed.id.value.toString
                    )
                  )
                case Left(error) =>
                  mapError(error)
          ,
          path(Segment / "ready-to-ship"): idStr =>
            post:
              val id = HandlingUnitId(UUID.fromString(idStr))
              onSuccess(
                lifecycleService
                  .readyToShip(id, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    HandlingUnitLifecycleResponse(
                      status = "ready-to-ship",
                      handlingUnitId = result.ready.id.value.toString
                    )
                  )
                case Left(error) =>
                  mapError(error)
          ,
          path(Segment / "ship"): idStr =>
            post:
              val id = HandlingUnitId(UUID.fromString(idStr))
              onSuccess(
                lifecycleService.ship(id, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    HandlingUnitLifecycleResponse(
                      status = "shipped",
                      handlingUnitId = result.shipped.id.value.toString
                    )
                  )
                case Left(error) =>
                  mapError(error)
          ,
          path(Segment / "empty"): idStr =>
            post:
              val id = HandlingUnitId(UUID.fromString(idStr))
              onSuccess(
                lifecycleService.empty(id, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    HandlingUnitLifecycleResponse(
                      status = "emptied",
                      handlingUnitId = result.empty.id.value.toString
                    )
                  )
                case Left(error) =>
                  mapError(error)
        )

  private def mapError(
      error: HandlingUnitLifecycleError
  ): Route =
    error match
      case _: HandlingUnitLifecycleError.HandlingUnitNotFound =>
        complete(StatusCodes.NotFound)
      case _: HandlingUnitLifecycleError.HandlingUnitInWrongState =>
        complete(StatusCodes.Conflict)
