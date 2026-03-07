package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.workstation.Workstation]]. Backed by a time-ordered UUID v7.
  */
opaque type WorkstationId = UUID

object WorkstationId:
  /** Generates a new WorkstationId backed by a time-ordered UUID v7. */
  def apply(): WorkstationId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a WorkstationId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): WorkstationId = value

  /** Returns the underlying UUID value. */
  extension (id: WorkstationId) def value: UUID = id
