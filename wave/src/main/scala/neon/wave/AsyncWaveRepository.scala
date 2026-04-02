package neon.wave

import neon.common.WaveId

import scala.concurrent.Future

/** Async port trait for [[Wave]] aggregate persistence and queries. */
trait AsyncWaveRepository:
  def findById(id: WaveId): Future[Option[Wave]]
  def save(wave: Wave, event: WaveEvent): Future[Unit]
