package neon.task

import neon.common.{HandlingUnitId, TaskId, WaveId}

/** Port trait for [[Task]] aggregate persistence and queries. */
trait TaskRepository:

  /** Finds a task by its unique identifier.
    *
    * @param id
    *   the task identifier
    * @return
    *   the task if it exists, [[None]] otherwise
    */
  def findById(id: TaskId): Option[Task]

  /** Finds all tasks belonging to the given wave.
    *
    * @param waveId
    *   the wave identifier
    * @return
    *   tasks in the wave, in any state
    */
  def findByWaveId(waveId: WaveId): List[Task]

  /** Finds all tasks associated with the given handling unit.
    *
    * @param handlingUnitId
    *   the handling unit identifier
    * @return
    *   tasks linked to the handling unit, in any state
    */
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[Task]

  /** Persists a single task state along with its event.
    *
    * @param task
    *   the current task state
    * @param event
    *   the event produced by the transition
    */
  def save(task: Task, event: TaskEvent): Unit

  /** Persists multiple task states and their events atomically.
    *
    * @param entries
    *   pairs of (task state, event) to persist
    */
  def saveAll(entries: List[(Task, TaskEvent)]): Unit
