package neon.wave

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

opaque type WaveId = UUID

object WaveId:
  def apply(): WaveId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): WaveId = value
  extension (id: WaveId) def value: UUID = id
