package neon.location

import neon.common.LocationId

import scala.concurrent.Future

/** Async port trait for [[Location]] reference data queries (read-only). */
trait AsyncLocationRepository:
  def findById(id: LocationId): Future[Option[Location]]
