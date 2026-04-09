package neon.cyclecount

import neon.common.CycleCountId

import scala.concurrent.Future

/** Async port trait for [[CycleCount]] aggregate persistence and queries. */
trait AsyncCycleCountRepository:
  def findById(id: CycleCountId): Future[Option[CycleCount]]
  def save(cycleCount: CycleCount, event: CycleCountEvent): Future[Unit]
