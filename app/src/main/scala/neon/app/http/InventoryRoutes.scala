package neon.app.http

import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, Permission, SkuId}
import neon.core.{AsyncInventoryService, InventoryError}
import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

object InventoryRoutes:

  case class CreateInventoryRequest(
      locationId: String,
      skuId: String,
      packagingLevel: String,
      lot: Option[String],
      onHand: Int
  ) derives Decoder

  case class InventoryQuantityRequest(quantity: Int) derives Decoder

  case class InventoryCorrectLotRequest(
      lot: Option[String]
  ) derives Decoder

  case class InventoryResponse(
      status: String,
      inventoryId: String
  ) derives Encoder.AsObject

  def apply(
      inventoryService: AsyncInventoryService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("inventory"):
      AuthDirectives.requirePermission(
        Permission.InventoryManage,
        authService
      ): _ =>
        concat(
          pathEnd:
            post:
              entity(as[CreateInventoryRequest]): request =>
                val locationId = LocationId(
                  UUID.fromString(request.locationId)
                )
                val skuId =
                  SkuId(UUID.fromString(request.skuId))
                val packagingLevel = PackagingLevel.valueOf(
                  request.packagingLevel
                )
                val lot = request.lot.map(Lot(_))
                onSuccess(
                  inventoryService.create(
                    locationId,
                    skuId,
                    packagingLevel,
                    lot,
                    request.onHand,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      InventoryResponse(
                        status = "created",
                        inventoryId = result.inventory.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "reserve"): idStr =>
            post:
              entity(as[InventoryQuantityRequest]): request =>
                val id = InventoryId(
                  UUID.fromString(idStr)
                )
                onSuccess(
                  inventoryService.reserve(
                    id,
                    request.quantity,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      InventoryResponse(
                        status = "reserved",
                        inventoryId = result.inventory.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "release"): idStr =>
            post:
              entity(as[InventoryQuantityRequest]): request =>
                val id = InventoryId(
                  UUID.fromString(idStr)
                )
                onSuccess(
                  inventoryService.release(
                    id,
                    request.quantity,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      InventoryResponse(
                        status = "released",
                        inventoryId = result.inventory.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "consume"): idStr =>
            post:
              entity(as[InventoryQuantityRequest]): request =>
                val id = InventoryId(
                  UUID.fromString(idStr)
                )
                onSuccess(
                  inventoryService.consume(
                    id,
                    request.quantity,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      InventoryResponse(
                        status = "consumed",
                        inventoryId = result.inventory.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "correct-lot"): idStr =>
            post:
              entity(as[InventoryCorrectLotRequest]): request =>
                val id = InventoryId(
                  UUID.fromString(idStr)
                )
                val newLot = request.lot.map(Lot(_))
                onSuccess(
                  inventoryService.correctLot(
                    id,
                    newLot,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      InventoryResponse(
                        status = "corrected",
                        inventoryId = result.inventory.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
        )

  private def mapError(error: InventoryError): Route =
    error match
      case _: InventoryError.InventoryNotFound =>
        complete(StatusCodes.NotFound)
      case _: InventoryError.InsufficientAvailable =>
        complete(StatusCodes.Conflict)
      case _: InventoryError.InsufficientReserved =>
        complete(StatusCodes.Conflict)
      case _: InventoryError.InvalidQuantity =>
        complete(StatusCodes.UnprocessableEntity)
      case _: InventoryError.ReservedNotZero =>
        complete(StatusCodes.Conflict)
