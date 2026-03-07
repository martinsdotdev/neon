package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for an [[neon.carrier.Carrier]]. Backed by a time-ordered UUID v7. */
opaque type CarrierId = UUID

object CarrierId:
  /** Generates a new CarrierId backed by a time-ordered UUID v7. */
  def apply(): CarrierId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a CarrierId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): CarrierId = value

  /** Returns the underlying UUID value. */
  extension (id: CarrierId) def value: UUID = id
