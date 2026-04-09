package neon.counttask

import neon.common.{CountTaskId, CycleCountId}

/** Port trait for [[CountTask]] aggregate persistence and queries. */
trait CountTaskRepository:

  /** Finds a count task by its unique identifier.
    *
    * @param id
    *   the count task identifier
    * @return
    *   the count task if it exists, [[None]] otherwise
    */
  def findById(id: CountTaskId): Option[CountTask]

  /** Finds all count tasks belonging to the given cycle count.
    *
    * @param cycleCountId
    *   the cycle count identifier
    * @return
    *   count tasks in the cycle count, in any state
    */
  def findByCycleCountId(cycleCountId: CycleCountId): List[CountTask]

  /** Persists a single count task state along with its event.
    *
    * @param countTask
    *   the current count task state
    * @param event
    *   the event produced by the transition
    */
  def save(countTask: CountTask, event: CountTaskEvent): Unit

  /** Persists multiple count task states and their events atomically.
    *
    * @param entries
    *   pairs of (count task state, event) to persist
    */
  def saveAll(entries: List[(CountTask, CountTaskEvent)]): Unit
