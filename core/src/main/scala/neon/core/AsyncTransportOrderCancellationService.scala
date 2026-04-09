package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.TransportOrderId
import neon.transportorder.{AsyncTransportOrderRepository, TransportOrder, TransportOrderEvent}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait TransportOrderCancellationError
object TransportOrderCancellationError:
  case class TransportOrderNotFound(id: TransportOrderId) extends TransportOrderCancellationError
  case class TransportOrderAlreadyTerminal(id: TransportOrderId)
      extends TransportOrderCancellationError

case class TransportOrderCancellationResult(
    cancelled: TransportOrder.Cancelled,
    event: TransportOrderEvent.TransportOrderCancelled
)

class AsyncTransportOrderCancellationService(
    transportOrderRepository: AsyncTransportOrderRepository
)(using ExecutionContext)
    extends LazyLogging:

  def cancel(
      id: TransportOrderId,
      at: Instant
  ): Future[Either[TransportOrderCancellationError, TransportOrderCancellationResult]] =
    transportOrderRepository
      .findById(id)
      .flatMap:
        case None =>
          Future.successful(Left(TransportOrderCancellationError.TransportOrderNotFound(id)))
        case Some(pending: TransportOrder.Pending) =>
          val (cancelled, event) = pending.cancel(at)
          transportOrderRepository
            .save(cancelled, event)
            .map(_ => Right(TransportOrderCancellationResult(cancelled, event)))
        case Some(_) =>
          Future.successful(Left(TransportOrderCancellationError.TransportOrderAlreadyTerminal(id)))
