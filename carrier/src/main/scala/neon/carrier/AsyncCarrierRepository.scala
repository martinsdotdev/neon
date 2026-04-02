package neon.carrier

import neon.common.CarrierId

import scala.concurrent.Future

/** Async port trait for [[Carrier]] reference data queries (read-only). */
trait AsyncCarrierRepository:
  def findById(id: CarrierId): Future[Option[Carrier]]
