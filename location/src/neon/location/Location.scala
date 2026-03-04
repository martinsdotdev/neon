package neon.location

import neon.common.{LocationId, ZoneId}

/** A physical warehouse location where inventory can be stored or retrieved.
  *
  * @param id
  *   the unique location identifier
  * @param code
  *   human-readable location code (e.g. aisle-bay-level)
  * @param zoneId
  *   the zone this location belongs to, if any
  * @param locationType
  *   the functional type of this location
  * @param pickingSequence
  *   optional sequence number for pick path optimization
  */
case class Location(
    id: LocationId,
    code: String,
    zoneId: Option[ZoneId],
    locationType: LocationType,
    pickingSequence: Option[Int] = None
)
