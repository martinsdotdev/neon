package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type ZoneId = UUID

object ZoneId:
  def apply(): ZoneId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): ZoneId = value
  extension (id: ZoneId) def value: UUID = id
