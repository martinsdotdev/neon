package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type OrderId = UUID

object OrderId:
  def apply(): OrderId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): OrderId = value
  extension (id: OrderId) def value: UUID = id
