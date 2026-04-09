package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.{HandlingUnitId, OrderId, SlotId}
import neon.slot.{AsyncSlotRepository, Slot, SlotEvent}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait SlotError
object SlotError:
  case class SlotNotFound(id: SlotId) extends SlotError
  case class SlotInWrongState(id: SlotId) extends SlotError

case class SlotReserveResult(
    reserved: Slot.Reserved,
    event: SlotEvent.SlotReserved
)
case class SlotCompleteResult(
    completed: Slot.Completed,
    event: SlotEvent.SlotCompleted
)
case class SlotReleaseResult(
    available: Slot.Available,
    event: SlotEvent.SlotReleased
)

class AsyncSlotService(
    slotRepository: AsyncSlotRepository
)(using ExecutionContext)
    extends LazyLogging:

  def reserve(
      id: SlotId,
      orderId: OrderId,
      handlingUnitId: HandlingUnitId,
      at: Instant
  ): Future[Either[SlotError, SlotReserveResult]] =
    slotRepository
      .findById(id)
      .flatMap:
        case None                            => Future.successful(Left(SlotError.SlotNotFound(id)))
        case Some(available: Slot.Available) =>
          val (reserved, event) = available.reserve(orderId, handlingUnitId, at)
          slotRepository.save(reserved, event).map(_ => Right(SlotReserveResult(reserved, event)))
        case Some(_) => Future.successful(Left(SlotError.SlotInWrongState(id)))

  def complete(
      id: SlotId,
      at: Instant
  ): Future[Either[SlotError, SlotCompleteResult]] =
    slotRepository
      .findById(id)
      .flatMap:
        case None                          => Future.successful(Left(SlotError.SlotNotFound(id)))
        case Some(reserved: Slot.Reserved) =>
          val (completed, event) = reserved.complete(at)
          slotRepository
            .save(completed, event)
            .map(_ => Right(SlotCompleteResult(completed, event)))
        case Some(_) => Future.successful(Left(SlotError.SlotInWrongState(id)))

  def release(
      id: SlotId,
      at: Instant
  ): Future[Either[SlotError, SlotReleaseResult]] =
    slotRepository
      .findById(id)
      .flatMap:
        case None                          => Future.successful(Left(SlotError.SlotNotFound(id)))
        case Some(reserved: Slot.Reserved) =>
          val (available, event) = reserved.release(at)
          slotRepository.save(available, event).map(_ => Right(SlotReleaseResult(available, event)))
        case Some(_) => Future.successful(Left(SlotError.SlotInWrongState(id)))
