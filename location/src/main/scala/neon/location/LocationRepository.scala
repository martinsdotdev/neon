package neon.location

import neon.common.LocationId

/** Port trait for [[Location]] reference data queries (read-only). */
trait LocationRepository:

  /** Finds a location by its unique identifier.
    *
    * @param id
    *   the location identifier
    * @return
    *   the location if it exists, [[None]] otherwise
    */
  def findById(id: LocationId): Option[Location]
