package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a physical or logical container. Backed by a time-ordered UUID v7. */
opaque type ContainerId = UUID

object ContainerId:
  /** Generates a new ContainerId backed by a time-ordered UUID v7. */
  def apply(): ContainerId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a ContainerId. */
  def apply(value: UUID): ContainerId = value

  /** Returns the underlying UUID value. */
  extension (id: ContainerId) def value: UUID = id
