package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type GroupId = UUID

object GroupId:
  def apply(): GroupId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): GroupId = value
  extension (id: GroupId) def value: UUID = id
