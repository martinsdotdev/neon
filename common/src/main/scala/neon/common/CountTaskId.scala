package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for an individual cycle count task. Backed by a time-ordered UUID v7. */
opaque type CountTaskId = UUID

object CountTaskId:
  /** Generates a new CountTaskId backed by a time-ordered UUID v7. */
  def apply(): CountTaskId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a CountTaskId. */
  def apply(value: UUID): CountTaskId = value

  /** Returns the underlying UUID value. */
  extension (id: CountTaskId) def value: UUID = id
