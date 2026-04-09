package neon.app.http

import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{
  AdjustmentReasonCode,
  Lot,
  LotAttributes,
  Permission,
  SkuId,
  StockPositionId,
  WarehouseAreaId
}
import neon.core.{AsyncStockPositionService, StockPositionError}
import io.circe.{Decoder, Encoder}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

object StockPositionRoutes:

  case class CreateStockPositionRequest(
      skuId: String,
      warehouseAreaId: String,
      lot: Option[String],
      onHandQuantity: Int
  ) derives Decoder

  case class StockPositionQuantityRequest(quantity: Int) derives Decoder

  case class StockPositionAdjustRequest(
      delta: Int,
      reasonCode: String
  ) derives Decoder

  case class StockPositionResponse(
      status: String,
      stockPositionId: String
  ) derives Encoder.AsObject

  def apply(
      stockPositionService: AsyncStockPositionService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("stock-positions"):
      AuthDirectives.requirePermission(
        Permission.StockManage,
        authService
      ): _ =>
        concat(
          pathEnd:
            post:
              entity(as[CreateStockPositionRequest]): request =>
                val skuId =
                  SkuId(UUID.fromString(request.skuId))
                val warehouseAreaId = WarehouseAreaId(
                  UUID.fromString(request.warehouseAreaId)
                )
                val lotAttributes = LotAttributes(
                  lot = request.lot.map(Lot(_))
                )
                onSuccess(
                  stockPositionService.create(
                    skuId,
                    warehouseAreaId,
                    lotAttributes,
                    request.onHandQuantity,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      StockPositionResponse(
                        status = "created",
                        stockPositionId = result.stockPosition.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "block"): idStr =>
            post:
              entity(as[StockPositionQuantityRequest]): request =>
                val id = StockPositionId(
                  UUID.fromString(idStr)
                )
                onSuccess(
                  stockPositionService.block(
                    id,
                    request.quantity,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      StockPositionResponse(
                        status = "blocked",
                        stockPositionId = result.stockPosition.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "unblock"): idStr =>
            post:
              entity(as[StockPositionQuantityRequest]): request =>
                val id = StockPositionId(
                  UUID.fromString(idStr)
                )
                onSuccess(
                  stockPositionService.unblock(
                    id,
                    request.quantity,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      StockPositionResponse(
                        status = "unblocked",
                        stockPositionId = result.stockPosition.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
          ,
          path(Segment / "adjust"): idStr =>
            post:
              entity(as[StockPositionAdjustRequest]): request =>
                val id = StockPositionId(
                  UUID.fromString(idStr)
                )
                val reasonCode = AdjustmentReasonCode.valueOf(
                  request.reasonCode
                )
                onSuccess(
                  stockPositionService.adjust(
                    id,
                    request.delta,
                    reasonCode,
                    Instant.now()
                  )
                ):
                  case Right(result) =>
                    complete(
                      StockPositionResponse(
                        status = "adjusted",
                        stockPositionId = result.stockPosition.id.value.toString
                      )
                    )
                  case Left(error) =>
                    mapError(error)
        )

  private def mapError(error: StockPositionError): Route =
    error match
      case _: StockPositionError.StockPositionNotFound =>
        complete(StatusCodes.NotFound)
      case _: StockPositionError.InvalidQuantity =>
        complete(StatusCodes.UnprocessableEntity)
      case _: StockPositionError.InsufficientAvailable =>
        complete(StatusCodes.Conflict)
      case _: StockPositionError.InsufficientBlocked =>
        complete(StatusCodes.Conflict)
