package neon.core

import neon.common.WaveId
import neon.wave.{Wave, WaveEvent, WaveRepository}

import scala.collection.mutable

class InMemoryWaveRepository extends WaveRepository:
  val store: mutable.Map[WaveId, Wave] = mutable.Map.empty
  val events: mutable.ListBuffer[WaveEvent] = mutable.ListBuffer.empty
  def findById(id: WaveId): Option[Wave] = store.get(id)
  def save(wave: Wave, event: WaveEvent): Unit =
    store(wave.id) = wave
    events += event
