package neon.core

import neon.common.LocationId
import neon.location.{Location, LocationRepository}

class InMemoryLocationRepository(initial: Map[LocationId, Location]) extends LocationRepository:
  def findById(id: LocationId): Option[Location] = initial.get(id)
