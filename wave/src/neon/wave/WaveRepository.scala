package neon.wave

import neon.common.WaveId

/** Port trait for Wave aggregate persistence and queries. */
trait WaveRepository:
  def findById(id: WaveId): Option[Wave]
  def save(wave: Wave, event: WaveEvent): Unit
