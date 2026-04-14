package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.transportorder.TransportOrder]]. Backed by a time-ordered UUID
  * v7.
  */
opaque type TransportOrderId = UUID

object TransportOrderId:
  /** Generates a new TransportOrderId backed by a time-ordered UUID v7. */
  def apply(): TransportOrderId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a TransportOrderId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): TransportOrderId = value

  /** Returns the underlying UUID value. */
  extension (id: TransportOrderId) def value: UUID = id
