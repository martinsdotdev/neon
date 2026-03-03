package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type TransportOrderId = UUID

object TransportOrderId:
  def apply(): TransportOrderId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): TransportOrderId = value
  extension (id: TransportOrderId) def value: UUID = id
