package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.stockposition.StockPosition]]. Backed by a time-ordered UUID v7.
  */
opaque type StockPositionId = UUID

object StockPositionId:
  /** Generates a new StockPositionId backed by a time-ordered UUID v7. */
  def apply(): StockPositionId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a StockPositionId. */
  def apply(value: UUID): StockPositionId = value

  /** Returns the underlying UUID value. */
  extension (id: StockPositionId) def value: UUID = id
