package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.wave.Wave]]. Backed by a time-ordered UUID v7. */
opaque type WaveId = UUID

object WaveId:
  /** Generates a new WaveId backed by a time-ordered UUID v7. */
  def apply(): WaveId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a WaveId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): WaveId = value

  /** Returns the underlying UUID value. */
  extension (id: WaveId) def value: UUID = id
