package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type LocationId = UUID

object LocationId:
  def apply(): LocationId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): LocationId = value
  extension (id: LocationId) def value: UUID = id
