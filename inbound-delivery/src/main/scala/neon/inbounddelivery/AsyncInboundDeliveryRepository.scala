package neon.inbounddelivery

import neon.common.InboundDeliveryId

import scala.concurrent.Future

/** Async port trait for [[InboundDelivery]] aggregate persistence and queries. */
trait AsyncInboundDeliveryRepository:
  def findById(id: InboundDeliveryId): Future[Option[InboundDelivery]]
  def save(delivery: InboundDelivery, event: InboundDeliveryEvent): Future[Unit]
