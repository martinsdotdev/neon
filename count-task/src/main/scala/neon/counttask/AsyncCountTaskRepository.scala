package neon.counttask

import neon.common.{CountTaskId, CycleCountId}

import scala.concurrent.Future

/** Async port trait for [[CountTask]] aggregate persistence and queries. */
trait AsyncCountTaskRepository:
  def findById(id: CountTaskId): Future[Option[CountTask]]
  def findByCycleCountId(cycleCountId: CycleCountId): Future[List[CountTask]]
  def save(countTask: CountTask, event: CountTaskEvent): Future[Unit]

  /** Persists multiple entries by fanning out to individual entity actors. Not transactional:
    * individual entries may succeed or fail independently.
    */
  def saveAll(entries: List[(CountTask, CountTaskEvent)]): Future[Unit]
