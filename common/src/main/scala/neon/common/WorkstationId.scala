package neon.common

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

opaque type WorkstationId = UUID

object WorkstationId:
  def apply(): WorkstationId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): WorkstationId = value
  extension (id: WorkstationId) def value: UUID = id
