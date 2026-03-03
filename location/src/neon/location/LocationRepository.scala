package neon.location

import neon.common.LocationId

/** Port trait for Location reference data queries (read-only). */
trait LocationRepository:
  def findById(id: LocationId): Option[Location]
