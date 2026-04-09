package neon.cyclecount

import neon.common.CycleCountId

/** Port trait for [[CycleCount]] aggregate persistence and queries. */
trait CycleCountRepository:

  /** Finds a cycle count by its unique identifier.
    *
    * @param id
    *   the cycle count identifier
    * @return
    *   the cycle count if it exists, [[None]] otherwise
    */
  def findById(id: CycleCountId): Option[CycleCount]

  /** Persists a cycle count state together with the event that caused the transition.
    *
    * @param cycleCount
    *   the cycle count state to persist
    * @param event
    *   the event produced by the transition
    */
  def save(cycleCount: CycleCount, event: CycleCountEvent): Unit
