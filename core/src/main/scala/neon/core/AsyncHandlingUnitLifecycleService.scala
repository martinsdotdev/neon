package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.HandlingUnitId
import neon.handlingunit.{AsyncHandlingUnitRepository, HandlingUnit, HandlingUnitEvent}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait HandlingUnitLifecycleError
object HandlingUnitLifecycleError:
  case class HandlingUnitNotFound(id: HandlingUnitId) extends HandlingUnitLifecycleError
  case class HandlingUnitInWrongState(id: HandlingUnitId) extends HandlingUnitLifecycleError

case class HandlingUnitPackResult(
    packed: HandlingUnit.Packed,
    event: HandlingUnitEvent.HandlingUnitPacked
)
case class HandlingUnitReadyToShipResult(
    ready: HandlingUnit.ReadyToShip,
    event: HandlingUnitEvent.HandlingUnitReadyToShip
)
case class HandlingUnitShipResult(
    shipped: HandlingUnit.Shipped,
    event: HandlingUnitEvent.HandlingUnitShipped
)
case class HandlingUnitEmptyResult(
    empty: HandlingUnit.Empty,
    event: HandlingUnitEvent.HandlingUnitEmptied
)

class AsyncHandlingUnitLifecycleService(
    handlingUnitRepository: AsyncHandlingUnitRepository
)(using ExecutionContext)
    extends LazyLogging:

  def pack(
      id: HandlingUnitId,
      at: Instant
  ): Future[Either[HandlingUnitLifecycleError, HandlingUnitPackResult]] =
    handlingUnitRepository
      .findById(id)
      .flatMap:
        case None => Future.successful(Left(HandlingUnitLifecycleError.HandlingUnitNotFound(id)))
        case Some(ship: HandlingUnit.ShipCreated) =>
          val (packed, event) = ship.pack(at)
          handlingUnitRepository
            .save(packed, event)
            .map(_ => Right(HandlingUnitPackResult(packed, event)))
        case Some(_) =>
          Future.successful(Left(HandlingUnitLifecycleError.HandlingUnitInWrongState(id)))

  def readyToShip(
      id: HandlingUnitId,
      at: Instant
  ): Future[Either[HandlingUnitLifecycleError, HandlingUnitReadyToShipResult]] =
    handlingUnitRepository
      .findById(id)
      .flatMap:
        case None => Future.successful(Left(HandlingUnitLifecycleError.HandlingUnitNotFound(id)))
        case Some(packed: HandlingUnit.Packed) =>
          val (ready, event) = packed.readyToShip(at)
          handlingUnitRepository
            .save(ready, event)
            .map(_ => Right(HandlingUnitReadyToShipResult(ready, event)))
        case Some(_) =>
          Future.successful(Left(HandlingUnitLifecycleError.HandlingUnitInWrongState(id)))

  def ship(
      id: HandlingUnitId,
      at: Instant
  ): Future[Either[HandlingUnitLifecycleError, HandlingUnitShipResult]] =
    handlingUnitRepository
      .findById(id)
      .flatMap:
        case None => Future.successful(Left(HandlingUnitLifecycleError.HandlingUnitNotFound(id)))
        case Some(ready: HandlingUnit.ReadyToShip) =>
          val (shipped, event) = ready.ship(at)
          handlingUnitRepository
            .save(shipped, event)
            .map(_ => Right(HandlingUnitShipResult(shipped, event)))
        case Some(_) =>
          Future.successful(Left(HandlingUnitLifecycleError.HandlingUnitInWrongState(id)))

  def empty(
      id: HandlingUnitId,
      at: Instant
  ): Future[Either[HandlingUnitLifecycleError, HandlingUnitEmptyResult]] =
    handlingUnitRepository
      .findById(id)
      .flatMap:
        case None => Future.successful(Left(HandlingUnitLifecycleError.HandlingUnitNotFound(id)))
        case Some(inBuffer: HandlingUnit.InBuffer) =>
          val (emptyHu, event) = inBuffer.empty(at)
          handlingUnitRepository
            .save(emptyHu, event)
            .map(_ => Right(HandlingUnitEmptyResult(emptyHu, event)))
        case Some(_) =>
          Future.successful(Left(HandlingUnitLifecycleError.HandlingUnitInWrongState(id)))
