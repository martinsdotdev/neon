package neon.task

import neon.common.{HandlingUnitId, TaskId, WaveId}

import scala.concurrent.Future

/** Async port trait for [[Task]] aggregate persistence and queries. */
trait AsyncTaskRepository:
  def findById(id: TaskId): Future[Option[Task]]
  def findByWaveId(waveId: WaveId): Future[List[Task]]
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): Future[List[Task]]
  def save(task: Task, event: TaskEvent): Future[Unit]

  /** Persists multiple entries by fanning out to individual entity actors. Not transactional:
    * individual entries may succeed or fail independently.
    */
  def saveAll(entries: List[(Task, TaskEvent)]): Future[Unit]
