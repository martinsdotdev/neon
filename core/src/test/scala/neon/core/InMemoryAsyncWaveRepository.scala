package neon.core

import neon.common.WaveId
import neon.wave.{AsyncWaveRepository, Wave, WaveEvent}

import scala.collection.mutable
import scala.concurrent.Future

class InMemoryAsyncWaveRepository(recorder: CallRecorder = CallRecorder())
    extends AsyncWaveRepository:
  val store: mutable.Map[WaveId, Wave] = mutable.Map.empty
  val events: mutable.ListBuffer[WaveEvent] = mutable.ListBuffer.empty

  def findById(id: WaveId): Future[Option[Wave]] =
    recorder.record("wave.findById")
    Future.successful(store.get(id))

  def save(wave: Wave, event: WaveEvent): Future[Unit] =
    recorder.record("wave.save")
    store(wave.id) = wave
    events += event
    Future.unit
