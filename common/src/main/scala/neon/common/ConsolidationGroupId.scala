package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.consolidationgroup.ConsolidationGroup]]. Backed by a time-ordered
  * UUID v7.
  */
opaque type ConsolidationGroupId = UUID

object ConsolidationGroupId:
  /** Generates a new ConsolidationGroupId backed by a time-ordered UUID v7. */
  def apply(): ConsolidationGroupId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a ConsolidationGroupId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): ConsolidationGroupId = value

  /** Returns the underlying UUID value. */
  extension (id: ConsolidationGroupId) def value: UUID = id
