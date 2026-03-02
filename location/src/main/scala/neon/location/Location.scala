package neon.location

import neon.common.{LocationId, ZoneId}

case class Location(
    id: LocationId,
    code: String,
    zoneId: Option[ZoneId],
    locationType: LocationType
)
