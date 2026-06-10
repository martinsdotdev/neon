package neon.core

import neon.carrier.{Carrier, CarrierRepository}
import neon.common.CarrierId

class InMemoryCarrierRepository(initial: Map[CarrierId, Carrier]) extends CarrierRepository:
  def findById(id: CarrierId): Option[Carrier] = initial.get(id)
