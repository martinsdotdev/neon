package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type HandlingUnitId = UUID

object HandlingUnitId:
  def apply(): HandlingUnitId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): HandlingUnitId = value
  extension (id: HandlingUnitId) def value: UUID = id
