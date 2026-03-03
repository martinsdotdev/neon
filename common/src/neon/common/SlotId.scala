package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type SlotId = UUID

object SlotId:
  def apply(): SlotId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): SlotId = value
  extension (id: SlotId) def value: UUID = id
