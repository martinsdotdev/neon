package neon.app.http

import io.circe.Encoder
import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{HandlingUnitId, LocationId, Permission, SkuId}
import neon.handlingunit.{AsyncHandlingUnitRepository, HandlingUnit}
import neon.location.AsyncLocationRepository
import neon.sku.AsyncSkuRepository
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

/** Read-only lookup endpoints for reference + aggregate data the mobile
  * client needs alongside a task view: SKU descriptions (for scan
  * verification), location codes (for navigation prompts), and handling
  * units (for HU label confirmation). Gated by Permission.TaskComplete
  * since these are read by anyone doing operator work.
  */
object MobileLookupRoutes:

  private case class SkuView(
      id: String,
      code: String,
      description: String,
      lotManaged: Boolean
  ) derives Encoder.AsObject

  private case class LocationView(
      id: String,
      code: String,
      locationType: String,
      zoneId: Option[String],
      pickingSequence: Option[Int]
  ) derives Encoder.AsObject

  private case class HandlingUnitView(
      id: String,
      state: String,
      packagingLevel: String
  ) derives Encoder.AsObject

  private def stateOf(hu: HandlingUnit): String =
    hu match
      case _: HandlingUnit.PickCreated  => "PickCreated"
      case _: HandlingUnit.InBuffer     => "InBuffer"
      case _: HandlingUnit.Empty        => "Empty"
      case _: HandlingUnit.ShipCreated  => "ShipCreated"
      case _: HandlingUnit.Packed       => "Packed"
      case _: HandlingUnit.ReadyToShip  => "ReadyToShip"
      case _: HandlingUnit.Shipped      => "Shipped"

  def apply(
      skuRepository: AsyncSkuRepository,
      locationRepository: AsyncLocationRepository,
      handlingUnitRepository: AsyncHandlingUnitRepository,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    concat(
      pathPrefix("skus"):
        AuthDirectives.requirePermission(
          Permission.TaskComplete,
          authService
        ): _ =>
          path(Segment): skuIdStr =>
            get:
              val skuId = SkuId(UUID.fromString(skuIdStr))
              onSuccess(skuRepository.findById(skuId)):
                case Some(sku) =>
                  complete(
                    SkuView(
                      id = sku.id.value.toString,
                      code = sku.code,
                      description = sku.description,
                      lotManaged = sku.lotManaged
                    )
                  )
                case None => complete(StatusCodes.NotFound)
      ,
      pathPrefix("locations"):
        AuthDirectives.requirePermission(
          Permission.TaskComplete,
          authService
        ): _ =>
          path(Segment): locationIdStr =>
            get:
              val locationId = LocationId(UUID.fromString(locationIdStr))
              onSuccess(locationRepository.findById(locationId)):
                case Some(location) =>
                  complete(
                    LocationView(
                      id = location.id.value.toString,
                      code = location.code,
                      locationType = location.locationType.toString,
                      zoneId = location.zoneId.map(_.value.toString),
                      pickingSequence = location.pickingSequence
                    )
                  )
                case None => complete(StatusCodes.NotFound)
      ,
      pathPrefix("handling-units"):
        AuthDirectives.requirePermission(
          Permission.TaskComplete,
          authService
        ): _ =>
          path(Segment): huIdStr =>
            get:
              val huId = HandlingUnitId(UUID.fromString(huIdStr))
              onSuccess(handlingUnitRepository.findById(huId)):
                case Some(hu) =>
                  complete(
                    HandlingUnitView(
                      id = hu.id.value.toString,
                      state = stateOf(hu),
                      packagingLevel = hu.packagingLevel.toString
                    )
                  )
                case None => complete(StatusCodes.NotFound)
    )
