package neon.wave

import neon.common.WaveId

/** Port trait for [[Wave]] aggregate persistence and queries. */
trait WaveRepository:

  /** Finds a wave by its unique identifier.
    *
    * @param id
    *   the wave identifier
    * @return
    *   the wave if it exists, [[None]] otherwise
    */
  def findById(id: WaveId): Option[Wave]

  /** Persists a wave state together with the event that caused the transition.
    *
    * @param wave
    *   the wave state to persist
    * @param event
    *   the event produced by the transition
    */
  def save(wave: Wave, event: WaveEvent): Unit
