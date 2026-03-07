package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for an [[neon.order.Order]]. Backed by a time-ordered UUID v7. */
opaque type OrderId = UUID

object OrderId:
  /** Generates a new OrderId backed by a time-ordered UUID v7. */
  def apply(): OrderId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as an OrderId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): OrderId = value

  /** Returns the underlying UUID value. */
  extension (id: OrderId) def value: UUID = id
