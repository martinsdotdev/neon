package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.slot.Slot]]. Backed by a time-ordered UUID v7. */
opaque type SlotId = UUID

object SlotId:
  /** Generates a new SlotId backed by a time-ordered UUID v7. */
  def apply(): SlotId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a SlotId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): SlotId = value

  /** Returns the underlying UUID value. */
  extension (id: SlotId) def value: UUID = id
