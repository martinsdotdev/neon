package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.{AdjustmentReasonCode, LotAttributes, SkuId, StockPositionId, WarehouseAreaId}
import neon.stockposition.{AsyncStockPositionRepository, StockPosition, StockPositionEvent}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait StockPositionError
object StockPositionError:
  case class StockPositionNotFound(id: StockPositionId) extends StockPositionError
  case class InvalidQuantity(id: StockPositionId) extends StockPositionError
  case class InsufficientAvailable(
      id: StockPositionId,
      requested: Int,
      available: Int
  ) extends StockPositionError
  case class InsufficientBlocked(
      id: StockPositionId,
      requested: Int,
      blocked: Int
  ) extends StockPositionError

case class StockPositionCreateResult(
    stockPosition: StockPosition,
    event: StockPositionEvent.Created
)
case class StockPositionMutationResult(
    stockPosition: StockPosition,
    event: StockPositionEvent
)

class AsyncStockPositionService(
    stockPositionRepository: AsyncStockPositionRepository
)(using ExecutionContext)
    extends LazyLogging:

  def create(
      skuId: SkuId,
      warehouseAreaId: WarehouseAreaId,
      lotAttributes: LotAttributes,
      onHandQuantity: Int,
      at: Instant
  ): Future[Either[StockPositionError, StockPositionCreateResult]] =
    val (sp, event) =
      StockPosition.create(skuId, warehouseAreaId, lotAttributes, onHandQuantity, at)
    stockPositionRepository
      .save(sp, event)
      .map(_ => Right(StockPositionCreateResult(sp, event)))

  def block(
      id: StockPositionId,
      quantity: Int,
      at: Instant
  ): Future[Either[StockPositionError, StockPositionMutationResult]] =
    stockPositionRepository
      .findById(id)
      .flatMap:
        case None =>
          Future.successful(Left(StockPositionError.StockPositionNotFound(id)))
        case Some(sp) =>
          if quantity <= 0 then Future.successful(Left(StockPositionError.InvalidQuantity(id)))
          else if quantity > sp.availableQuantity then
            Future.successful(
              Left(
                StockPositionError
                  .InsufficientAvailable(id, quantity, sp.availableQuantity)
              )
            )
          else
            val (updated, event) = sp.block(quantity, at)
            stockPositionRepository
              .save(updated, event)
              .map(_ => Right(StockPositionMutationResult(updated, event)))

  def unblock(
      id: StockPositionId,
      quantity: Int,
      at: Instant
  ): Future[Either[StockPositionError, StockPositionMutationResult]] =
    stockPositionRepository
      .findById(id)
      .flatMap:
        case None =>
          Future.successful(Left(StockPositionError.StockPositionNotFound(id)))
        case Some(sp) =>
          if quantity <= 0 then Future.successful(Left(StockPositionError.InvalidQuantity(id)))
          else if quantity > sp.blockedQuantity then
            Future.successful(
              Left(
                StockPositionError
                  .InsufficientBlocked(id, quantity, sp.blockedQuantity)
              )
            )
          else
            val (updated, event) = sp.unblock(quantity, at)
            stockPositionRepository
              .save(updated, event)
              .map(_ => Right(StockPositionMutationResult(updated, event)))

  def adjust(
      id: StockPositionId,
      delta: Int,
      reasonCode: AdjustmentReasonCode,
      at: Instant
  ): Future[Either[StockPositionError, StockPositionMutationResult]] =
    stockPositionRepository
      .findById(id)
      .flatMap:
        case None =>
          Future.successful(Left(StockPositionError.StockPositionNotFound(id)))
        case Some(sp) =>
          val (updated, event) = sp.adjust(delta, reasonCode, at)
          stockPositionRepository
            .save(updated, event)
            .map(_ => Right(StockPositionMutationResult(updated, event)))
