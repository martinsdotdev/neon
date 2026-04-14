package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for an [[neon.inventory.Inventory]] position. Backed by a time-ordered UUID
  * v7.
  */
opaque type InventoryId = UUID

object InventoryId:
  /** Generates a new InventoryId backed by a time-ordered UUID v7. */
  def apply(): InventoryId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as an InventoryId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): InventoryId = value

  /** Returns the underlying UUID value. */
  extension (id: InventoryId) def value: UUID = id
