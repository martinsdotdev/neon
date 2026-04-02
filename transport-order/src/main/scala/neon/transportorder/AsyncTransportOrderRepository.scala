package neon.transportorder

import neon.common.{HandlingUnitId, TransportOrderId}

import scala.concurrent.Future

/** Async port trait for [[TransportOrder]] aggregate persistence and queries. */
trait AsyncTransportOrderRepository:
  def findById(id: TransportOrderId): Future[Option[TransportOrder]]
  def findByHandlingUnitId(
      handlingUnitId: HandlingUnitId
  ): Future[List[TransportOrder]]
  def save(
      transportOrder: TransportOrder,
      event: TransportOrderEvent
  ): Future[Unit]
  def saveAll(
      entries: List[(TransportOrder, TransportOrderEvent)]
  ): Future[Unit]
