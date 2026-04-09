package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, SkuId}
import neon.inventory.{AsyncInventoryRepository, Inventory, InventoryEvent}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait InventoryError
object InventoryError:
  case class InventoryNotFound(id: InventoryId) extends InventoryError
  case class InsufficientAvailable(
      id: InventoryId,
      requested: Int,
      available: Int
  ) extends InventoryError
  case class InsufficientReserved(
      id: InventoryId,
      requested: Int,
      reserved: Int
  ) extends InventoryError
  case class InvalidQuantity(id: InventoryId) extends InventoryError
  case class ReservedNotZero(id: InventoryId) extends InventoryError

case class InventoryCreateResult(
    inventory: Inventory,
    event: InventoryEvent.InventoryCreated
)
case class InventoryMutationResult(
    inventory: Inventory,
    event: InventoryEvent
)

class AsyncInventoryService(
    inventoryRepository: AsyncInventoryRepository
)(using ExecutionContext)
    extends LazyLogging:

  def create(
      locationId: LocationId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lot: Option[Lot],
      onHand: Int,
      at: Instant
  ): Future[Either[InventoryError, InventoryCreateResult]] =
    val (inventory, event) = Inventory.create(locationId, skuId, packagingLevel, lot, onHand, at)
    inventoryRepository
      .save(inventory, event)
      .map(_ => Right(InventoryCreateResult(inventory, event)))

  def reserve(
      id: InventoryId,
      quantity: Int,
      at: Instant
  ): Future[Either[InventoryError, InventoryMutationResult]] =
    inventoryRepository
      .findById(id)
      .flatMap:
        case None      => Future.successful(Left(InventoryError.InventoryNotFound(id)))
        case Some(inv) =>
          if quantity <= 0 then Future.successful(Left(InventoryError.InvalidQuantity(id)))
          else if quantity > inv.available then
            Future.successful(
              Left(InventoryError.InsufficientAvailable(id, quantity, inv.available))
            )
          else
            val (updated, event) = inv.reserve(quantity, at)
            inventoryRepository
              .save(updated, event)
              .map(_ => Right(InventoryMutationResult(updated, event)))

  def release(
      id: InventoryId,
      quantity: Int,
      at: Instant
  ): Future[Either[InventoryError, InventoryMutationResult]] =
    inventoryRepository
      .findById(id)
      .flatMap:
        case None      => Future.successful(Left(InventoryError.InventoryNotFound(id)))
        case Some(inv) =>
          if quantity <= 0 then Future.successful(Left(InventoryError.InvalidQuantity(id)))
          else if quantity > inv.reserved then
            Future.successful(Left(InventoryError.InsufficientReserved(id, quantity, inv.reserved)))
          else
            val (updated, event) = inv.release(quantity, at)
            inventoryRepository
              .save(updated, event)
              .map(_ => Right(InventoryMutationResult(updated, event)))

  def consume(
      id: InventoryId,
      quantity: Int,
      at: Instant
  ): Future[Either[InventoryError, InventoryMutationResult]] =
    inventoryRepository
      .findById(id)
      .flatMap:
        case None      => Future.successful(Left(InventoryError.InventoryNotFound(id)))
        case Some(inv) =>
          if quantity <= 0 then Future.successful(Left(InventoryError.InvalidQuantity(id)))
          else if quantity > inv.reserved then
            Future.successful(Left(InventoryError.InsufficientReserved(id, quantity, inv.reserved)))
          else
            val (updated, event) = inv.consume(quantity, at)
            inventoryRepository
              .save(updated, event)
              .map(_ => Right(InventoryMutationResult(updated, event)))

  def correctLot(
      id: InventoryId,
      newLot: Option[Lot],
      at: Instant
  ): Future[Either[InventoryError, InventoryMutationResult]] =
    inventoryRepository
      .findById(id)
      .flatMap:
        case None      => Future.successful(Left(InventoryError.InventoryNotFound(id)))
        case Some(inv) =>
          if inv.reserved != 0 then Future.successful(Left(InventoryError.ReservedNotZero(id)))
          else
            val (updated, event) = inv.correctLot(newLot, at)
            inventoryRepository
              .save(updated, event)
              .map(_ => Right(InventoryMutationResult(updated, event)))
