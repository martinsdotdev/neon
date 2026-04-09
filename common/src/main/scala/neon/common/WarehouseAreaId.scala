package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a warehouse area. Backed by a time-ordered UUID v7. */
opaque type WarehouseAreaId = UUID

object WarehouseAreaId:
  /** Generates a new WarehouseAreaId backed by a time-ordered UUID v7. */
  def apply(): WarehouseAreaId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a WarehouseAreaId. */
  def apply(value: UUID): WarehouseAreaId = value

  /** Returns the underlying UUID value. */
  extension (id: WarehouseAreaId) def value: UUID = id
