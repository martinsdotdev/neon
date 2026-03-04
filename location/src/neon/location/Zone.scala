package neon.location

import neon.common.ZoneId

/** A warehouse zone grouping related [[Location]]s for operational management.
  *
  * @param id
  *   the unique zone identifier
  * @param code
  *   human-readable zone code
  * @param description
  *   descriptive name of the zone
  */
case class Zone(
    id: ZoneId,
    code: String,
    description: String
)
