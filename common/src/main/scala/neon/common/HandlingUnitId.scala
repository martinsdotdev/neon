package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.handlingunit.HandlingUnit]]. Backed by a time-ordered UUID v7.
  */
opaque type HandlingUnitId = UUID

object HandlingUnitId:
  /** Generates a new HandlingUnitId backed by a time-ordered UUID v7. */
  def apply(): HandlingUnitId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a HandlingUnitId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): HandlingUnitId = value

  /** Returns the underlying UUID value. */
  extension (id: HandlingUnitId) def value: UUID = id
