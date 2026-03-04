package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.consolidationgroup.ConsolidationGroup]]. Backed by a time-ordered UUID
  * v7.
  */
opaque type GroupId = UUID

object GroupId:
  /** Generates a new GroupId backed by a time-ordered UUID v7. */
  def apply(): GroupId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a GroupId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): GroupId = value

  /** Returns the underlying UUID value. */
  extension (id: GroupId) def value: UUID = id
