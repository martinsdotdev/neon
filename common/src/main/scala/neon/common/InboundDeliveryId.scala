package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for an inbound delivery (ASN/receiving plan). Backed by a time-ordered UUID
  * v7.
  */
opaque type InboundDeliveryId = UUID

object InboundDeliveryId:
  /** Generates a new InboundDeliveryId backed by a time-ordered UUID v7. */
  def apply(): InboundDeliveryId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as an InboundDeliveryId. */
  def apply(value: UUID): InboundDeliveryId = value

  /** Returns the underlying UUID value. */
  extension (id: InboundDeliveryId) def value: UUID = id
