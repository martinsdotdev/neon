package neon.common

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

opaque type UserId = UUID

object UserId:
  def apply(): UserId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): UserId = value
  extension (id: UserId) def value: UUID = id
