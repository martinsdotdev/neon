package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a cycle count order. Backed by a time-ordered UUID v7. */
opaque type CycleCountId = UUID

object CycleCountId:
  /** Generates a new CycleCountId backed by a time-ordered UUID v7. */
  def apply(): CycleCountId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a CycleCountId. */
  def apply(value: UUID): CycleCountId = value

  /** Returns the underlying UUID value. */
  extension (id: CycleCountId) def value: UUID = id
