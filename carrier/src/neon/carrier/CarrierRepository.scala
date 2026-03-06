package neon.carrier

import neon.common.CarrierId

/** Port trait for [[Carrier]] reference data queries (read-only). */
trait CarrierRepository:

  /** Finds a carrier by its unique identifier.
    *
    * @param id
    *   the carrier identifier
    * @return
    *   the carrier if it exists, [[None]] otherwise
    */
  def findById(id: CarrierId): Option[Carrier]
