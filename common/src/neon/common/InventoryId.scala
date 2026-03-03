package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type InventoryId = UUID

object InventoryId:
  def apply(): InventoryId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): InventoryId = value
  extension (id: InventoryId) def value: UUID = id
