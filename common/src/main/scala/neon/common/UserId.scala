package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.user.User]]. Backed by a time-ordered UUID v7. */
opaque type UserId = UUID

object UserId:
  /** Generates a new UserId backed by a time-ordered UUID v7. */
  def apply(): UserId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a UserId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): UserId = value

  /** Returns the underlying UUID value. */
  extension (id: UserId) def value: UUID = id
