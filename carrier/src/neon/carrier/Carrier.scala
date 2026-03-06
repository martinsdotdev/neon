package neon.carrier

import neon.common.CarrierId

/** Carrier reference data.
  *
  * @param id
  *   the unique carrier identifier
  * @param code
  *   human-readable carrier code
  * @param name
  *   display name
  * @param active
  *   whether this carrier is available for wave planning
  */
case class Carrier(
    id: CarrierId,
    code: String,
    name: String,
    active: Boolean
)
