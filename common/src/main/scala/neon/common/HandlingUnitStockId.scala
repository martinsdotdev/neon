package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.handlingunitstock.HandlingUnitStock]]. Backed by a time-ordered
  * UUID v7.
  */
opaque type HandlingUnitStockId = UUID

object HandlingUnitStockId:
  /** Generates a new HandlingUnitStockId backed by a time-ordered UUID v7. */
  def apply(): HandlingUnitStockId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a HandlingUnitStockId. */
  def apply(value: UUID): HandlingUnitStockId = value

  /** Returns the underlying UUID value. */
  extension (id: HandlingUnitStockId) def value: UUID = id
