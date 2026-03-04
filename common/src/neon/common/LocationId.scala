package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.location.Location]]. Backed by a time-ordered UUID v7.
  */
opaque type LocationId = UUID

object LocationId:
  /** Generates a new LocationId backed by a time-ordered UUID v7. */
  def apply(): LocationId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a LocationId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): LocationId = value

  /** Returns the underlying UUID value. */
  extension (id: LocationId) def value: UUID = id
