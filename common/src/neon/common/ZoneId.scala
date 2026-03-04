package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.location.Zone]]. Backed by a time-ordered UUID v7.
  */
opaque type ZoneId = UUID

object ZoneId:
  /** Generates a new ZoneId backed by a time-ordered UUID v7. */
  def apply(): ZoneId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a ZoneId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): ZoneId = value

  /** Returns the underlying UUID value. */
  extension (id: ZoneId) def value: UUID = id
