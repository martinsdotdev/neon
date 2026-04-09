package neon.common

/** Identifies a slot position within a container. Backed by a non-empty string. */
opaque type SlotCode = String

object SlotCode:
  /** Wraps a non-empty string as a SlotCode.
    *
    * @param value
    *   the slot code string; must not be blank
    */
  def apply(value: String): SlotCode =
    require(value.nonEmpty, "SlotCode must not be empty")
    value

  /** Returns the underlying string value. */
  extension (code: SlotCode) def value: String = code
